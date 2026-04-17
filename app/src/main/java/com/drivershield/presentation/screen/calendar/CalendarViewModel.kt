@file:Suppress("SpellCheckingInspection")
package com.drivershield.presentation.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.model.DayOverride
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.EventType
import com.drivershield.domain.model.SessionReport
import com.drivershield.domain.repository.DayOverrideRepository
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.domain.usecase.ToggleDayOverrideUseCase
import com.drivershield.domain.util.NightShiftSplitter
// CICLO ALTERNO — DESACTIVADO: import CycleCalculator comentado
// import com.drivershield.domain.util.CycleCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import javax.inject.Inject

// Límites legales de días de trabajo por semana (mín. 1 descanso semanal → máx. 6 trabajo)
private const val MIN_WORK_DAYS = 4
private const val MAX_WORK_DAYS = 6

enum class DayStatus { GREEN_POINT, ORANGE_POINT }
enum class BlockType { WORK, REST, LIBRANCE, PLANNED, NEUTRAL }

/** Origen del estado de libranza para diferenciarlo visualmente en la UI LCD. */
enum class LibranzaSource { FIXED, MANUAL }

data class TimeBlock(
    val startHour: Float,
    val endHour: Float,
    val type: BlockType
)

data class DayState(
    val date: LocalDate,
    val dayOfWeek: Int,
    val totalEffectiveHours: Float,
    val totalRestHours: Float,
    val progressiveWeeklyHours: Float,
    val status: DayStatus,
    val timeBlocks: List<TimeBlock>,
    val isOffDay: Boolean = false,
    val configStartHour: Int = 8,
    val configEndHour: Int = 18,
    val isLibranzaDay: Boolean = false,
    val libranzaSource: LibranzaSource? = null
)

data class CalendarUiState(
    val weekYear: Int,
    val weekNumber: Int,
    val weekDays: List<DayState>,
    val totalWeeklyHours: Float,
    val weeklyTargetDays: Int = 5,
    /** Horas planificadas = (7 - fijos ± manuales) × horas_día. Límite dinámico del conductor. */
    val weeklyTargetHours: Float = 0f,
    val isLoading: Boolean = false
)

private data class ConfigState(
    val offDays: List<Int>,
    val alternateOffDays: List<Int>,
    val weeksToRotate: Int,
    val startHour: Int,
    val endHour: Int,
    val nextAltReference: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    scheduleRepository: ScheduleRepository,
    sessionDataStore: SessionDataStore,
    private val dayOverrideRepository: DayOverrideRepository,
    private val toggleDayOverrideUseCase: ToggleDayOverrideUseCase
) : ViewModel() {

    private val today = LocalDate.now()
    private val weekFields = WeekFields.ISO

    // ── Navegación semanal ────────────────────────────────────────────────
    private val _selectedPage = MutableStateFlow(INITIAL_PAGE)
    val selectedPage: StateFlow<Int> = _selectedPage.asStateFlow()

    /** Evento de un solo disparo para mostrar mensajes de validación en la UI. */
    private val _validationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val validationEvent: SharedFlow<String> = _validationEvent.asSharedFlow()

    fun setPage(page: Int) {
        _selectedPage.value = page.coerceIn(0, TOTAL_PAGES - 1)
    }

    /**
     * Invierte el estado de libranza del día dado.
     * Valida que la semana resultante tenga entre [MIN_WORK_DAYS] y [MAX_WORK_DAYS] días laborables.
     */
    fun toggleDayOverride(date: LocalDate) {
        val fixedOffDays = _currentFixedOffDays
        viewModelScope.launch {
            toggleDayOverrideUseCase(date, fixedOffDays)
        }
    }

    /** Captura los offDays fijos actuales para pasarlos al UseCase sin leer DataStore desde él. */
    private var _currentFixedOffDays: List<Int> = emptyList()

    private val _configFlow = combine(
        combine(
            sessionDataStore.offDays,
            sessionDataStore.alternateOffDays,
            sessionDataStore.weeksToRotate
        ) { offDays, altDays, weeks -> Triple(offDays, altDays, weeks) },
        sessionDataStore.startHour,
        sessionDataStore.endHour,
        scheduleRepository.getScheduleFlow(),
        sessionDataStore.nextAltReference
    ) { offConfig, startHour, endHour, _, nextAltRef ->
        _currentFixedOffDays = offConfig.first
        ConfigState(
            offDays = offConfig.first,
            alternateOffDays = offConfig.second,
            weeksToRotate = offConfig.third,
            startHour = startHour,
            endHour = endHour,
            nextAltReference = nextAltRef
        )
    }.distinctUntilChanged()

    val uiState: StateFlow<CalendarUiState> = combine(_configFlow, _selectedPage) { config, page ->
        config to page
    }.flatMapLatest { (config, page) ->
        val weekOffset = (page - INITIAL_PAGE).toLong()
        val weekStart = today.with(weekFields.dayOfWeek(), 1).plusWeeks(weekOffset)
        val weekEnd = weekStart.plusDays(6L)
        val isoYear = weekStart.get(weekFields.weekBasedYear())
        val weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear())

        combine(
            shiftRepository.getAllSessionsWithEvents().distinctUntilChanged(),
            dayOverrideRepository.getOverridesInRange(weekStart, weekEnd).distinctUntilChanged()
        ) { dayReports: List<DayReport>, overrides: List<DayOverride> ->
            withContext(Dispatchers.Default) {
                buildCalendarState(
                    overrides = overrides,
                    userOffDays = config.offDays,
                    weekStart = weekStart,
                    currentIsoYear = isoYear,
                    currentWeekNumber = weekNumber,
                    configStartHour = config.startHour,
                    configEndHour = config.endHour,
                    allSessions = dayReports.flatMap { it.sessions }
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState(
            weekYear = today.get(WeekFields.ISO.weekBasedYear()),
            weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear()),
            weekDays = emptyList(),
            totalWeeklyHours = 0f,
            isLoading = true
        )
    )

    private fun buildCalendarState(
        overrides: List<DayOverride>,
        userOffDays: List<Int>,
        weekStart: LocalDate,
        currentIsoYear: Int,
        currentWeekNumber: Int,
        configStartHour: Int,
        configEndHour: Int,
        allSessions: List<SessionReport>
    ): CalendarUiState {
        val zone = ZoneId.systemDefault()

        // ── 1. Fragmentar horas de trabajo por día natural ────────────────
        val workMsPerDay = mutableMapOf<LocalDate, Long>()
        val restMsPerDay = mutableMapOf<LocalDate, Long>()

        allSessions.forEach { session ->
            val endTs = session.endTimestamp ?: return@forEach  // sesión activa: excluir
            NightShiftSplitter.msPerDay(session.startTimestamp, endTs, zone).forEach { (date, ms) ->
                workMsPerDay[date] = (workMsPerDay[date] ?: 0L) + ms
            }
            // Las pausas se imputan al día de inicio del turno (las pausas raramente cruzan medianoche)
            val sessionDate = Instant.ofEpochMilli(session.startTimestamp).atZone(zone).toLocalDate()
            restMsPerDay[sessionDate] = (restMsPerDay[sessionDate] ?: 0L) + session.totalRestMs
        }

        // ── 2. Mapa de overrides indexado por fecha ───────────────────────
        val overrideMap: Map<LocalDate, DayOverride> = overrides.associateBy { it.date }

        // ── 3. Construir DayState para cada día de la semana ─────────────
        val weekDays = (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val dayOfWeek = dayOffset + 1   // 1=Lun … 7=Dom (ISO)

            val isFixedOff = userOffDays.contains(dayOfWeek)
            val override = overrideMap[date]
            val finalIsLibranza = override?.isLibranza ?: isFixedOff

            val libranzaSource: LibranzaSource? = when {
                !finalIsLibranza                              -> null                  // día laborable
                override != null && override.isLibranza && !isFixedOff -> LibranzaSource.MANUAL
                else                                          -> LibranzaSource.FIXED
            }

            val effectiveMs = workMsPerDay[date] ?: 0L
            val restMs = restMsPerDay[date] ?: 0L
            val effectiveHours = effectiveMs / 3_600_000f
            val restHours = restMs / 3_600_000f

            val daySessions = allSessions.filter { session ->
                Instant.ofEpochMilli(session.startTimestamp).atZone(zone).toLocalDate() == date
            }
            val timeBlocks = buildTimeBlocks(daySessions, configStartHour, configEndHour)
            val status = if (effectiveHours > 0f) DayStatus.GREEN_POINT else DayStatus.ORANGE_POINT

            DayState(
                date = date,
                dayOfWeek = dayOfWeek,
                totalEffectiveHours = effectiveHours,
                totalRestHours = restHours,
                progressiveWeeklyHours = 0f,
                status = status,
                timeBlocks = timeBlocks,
                isOffDay = isFixedOff,
                configStartHour = configStartHour,
                configEndHour = configEndHour,
                isLibranzaDay = finalIsLibranza,
                libranzaSource = libranzaSource
            )
        }

        // ── 4. Acumulado semanal progresivo ──────────────────────────────
        var accumulated = 0f
        val weekDaysWithAccumulated = weekDays.map { day ->
            accumulated += day.totalEffectiveHours
            day.copy(progressiveWeeklyHours = accumulated)
        }

        val weeklyTargetDays = weekDays.count { !it.isLibranzaDay }.coerceAtLeast(1)

        // Límite dinámico: días laborables × horas configuradas por día
        val dailyConfigHours: Float = if (configEndHour > configStartHour) {
            (configEndHour - configStartHour).toFloat()
        } else if (configEndHour < configStartHour) {
            (24 - configStartHour + configEndHour).toFloat()   // turno nocturno
        } else {
            0f
        }
        val weeklyTargetHours = weeklyTargetDays.toFloat() * dailyConfigHours

        return CalendarUiState(
            weekYear = currentIsoYear,
            weekNumber = currentWeekNumber,
            weekDays = weekDaysWithAccumulated,
            totalWeeklyHours = accumulated,
            weeklyTargetDays = weeklyTargetDays,
            weeklyTargetHours = weeklyTargetHours,
            isLoading = false
        )
    }

    private fun buildTimeBlocks(sessions: List<SessionReport>, configStartHour: Int, configEndHour: Int): List<TimeBlock> {
        val blocks = mutableListOf<TimeBlock>()
        val isOvernight = configEndHour < configStartHour
        val plannedRanges = if (isOvernight) {
            listOf(configStartHour..23, 0 until configEndHour)
        } else {
            listOf(configStartHour until configEndHour)
        }

        plannedRanges.forEach { range ->
            blocks.add(TimeBlock(range.first.toFloat(), (range.last + 1).toFloat(), BlockType.PLANNED))
        }

        sessions.forEach { session ->
            if (session.events.isEmpty()) return@forEach

            var lastWorkStart: Long? = null
            var lastRestStart: Long? = null

            session.events.sortedBy { it.timestamp }.forEach { event ->
                when (event.type) {
                    EventType.START_WORK -> lastWorkStart = event.timestamp
                    EventType.START_PAUSE -> {
                        lastWorkStart?.let { start ->
                            blocks.add(createBlock(start, event.timestamp, BlockType.WORK))
                            lastWorkStart = null
                        }
                        lastRestStart = event.timestamp
                    }
                    EventType.END_PAUSE -> {
                        lastRestStart?.let { start ->
                            blocks.add(createBlock(start, event.timestamp, BlockType.REST))
                            lastRestStart = null
                        }
                        lastWorkStart = event.timestamp
                    }
                    EventType.END_SHIFT -> {
                        lastWorkStart?.let { start ->
                            blocks.add(createBlock(start, event.timestamp, BlockType.WORK))
                            lastWorkStart = null
                        }
                    }
                }
            }
        }
        return blocks.sortedBy { it.startHour }
    }

    private fun createBlock(startMs: Long, endMs: Long, type: BlockType): TimeBlock {
        val zone = ZoneId.systemDefault()
        val startLocal = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDateTime()
        val endLocal = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDateTime()
        val startHour = startLocal.hour + startLocal.minute / 60f
        val endHour = endLocal.hour + endLocal.minute / 60f
        return TimeBlock(startHour, endHour, type)
    }

    companion object {
        const val WEEKS_BEFORE = 9    // ~2 meses antes
        const val WEEKS_AFTER  = 13   // ~3 meses después
        const val TOTAL_PAGES  = WEEKS_BEFORE + 1 + WEEKS_AFTER  // 23 páginas
        const val INITIAL_PAGE = WEEKS_BEFORE                    // página inicial = semana actual
    }
}
