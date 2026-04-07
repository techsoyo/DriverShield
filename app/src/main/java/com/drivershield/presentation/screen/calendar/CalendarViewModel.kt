@file:Suppress("SpellCheckingInspection")
package com.drivershield.presentation.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.EventType
import com.drivershield.domain.model.SessionReport
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.domain.util.CycleCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import javax.inject.Inject

enum class DayStatus { GREEN_POINT, ORANGE_POINT }
enum class BlockType { WORK, REST, LIBRANCE, PLANNED, NEUTRAL }

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
    val isLibranzaDay: Boolean = false
)

data class CalendarUiState(
    val weekYear: Int,
    val weekNumber: Int,
    val weekDays: List<DayState>,
    val totalWeeklyHours: Float,
    val isLoading: Boolean = false
)

private data class ConfigState(
    val offDays: List<Int>,
    val startHour: Int,
    val endHour: Int,
    val cycleStartEpoch: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    shiftRepository: ShiftRepository,
    scheduleRepository: ScheduleRepository,
    sessionDataStore: SessionDataStore
) : ViewModel() {

    private val today = LocalDate.now()
    private val weekFields = WeekFields.ISO

    // ── Navegación semanal ────────────────────────────────────────────────
    private val _selectedPage = MutableStateFlow(INITIAL_PAGE)
    val selectedPage: StateFlow<Int> = _selectedPage.asStateFlow()

    fun setPage(page: Int) {
        _selectedPage.value = page.coerceIn(0, TOTAL_PAGES - 1)
    }

    private val _configFlow = combine(
        sessionDataStore.offDays,
        sessionDataStore.startHour,
        sessionDataStore.endHour,
        scheduleRepository.getScheduleFlow()
    ) { offDays, startHour, endHour, schedule ->
        ConfigState(offDays, startHour, endHour, schedule?.cycleStartEpoch ?: 0L)
    }

    val uiState: StateFlow<CalendarUiState> = combine(_configFlow, _selectedPage) { config, page ->
        config to page
    }.flatMapLatest { (config, page) ->
        val weekOffset = (page - INITIAL_PAGE).toLong()
        val weekStart = today.with(weekFields.dayOfWeek(), 1).plusWeeks(weekOffset)
        val isoYear = weekStart.get(weekFields.weekBasedYear())
        val weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear())

        shiftRepository.getAllSessionsWithEvents()
            .map { dayReports ->
                withContext(Dispatchers.Default) {
                    buildCalendarState(
                        dayReports = dayReports,
                        userOffDays = config.offDays,
                        weekStart = weekStart,
                        currentIsoYear = isoYear,
                        currentWeekNumber = weekNumber,
                        configStartHour = config.startHour,
                        configEndHour = config.endHour,
                        cycleStartEpoch = config.cycleStartEpoch
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
        dayReports: List<DayReport>,
        userOffDays: List<Int>,
        weekStart: LocalDate,
        currentIsoYear: Int,
        currentWeekNumber: Int,
        configStartHour: Int,
        configEndHour: Int,
        cycleStartEpoch: Long
    ): CalendarUiState {

        val weekDays = (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val dayOfWeek = dayOffset + 1

            val isLibranzaDay = CycleCalculator.isOffDay(date, cycleStartEpoch, userOffDays)

            val daySessions = dayReports
                .filter { it.date == date }
                .flatMap { it.sessions }

            val effectiveMs = daySessions.sumOf { it.totalWorkMs }
            val restMs = daySessions.sumOf { it.totalRestMs }

            val effectiveHours = effectiveMs / 3_600_000f
            val restHours = restMs / 3_600_000f

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
                isOffDay = userOffDays.contains(dayOfWeek),
                configStartHour = configStartHour,
                configEndHour = configEndHour,
                isLibranzaDay = isLibranzaDay
            )
        }

        var accumulated = 0f
        val weekDaysWithAccumulated = weekDays.map { day ->
            accumulated += day.totalEffectiveHours
            day.copy(progressiveWeeklyHours = accumulated)
        }

        return CalendarUiState(
            weekYear = currentIsoYear,
            weekNumber = currentWeekNumber,
            weekDays = weekDaysWithAccumulated,
            totalWeeklyHours = accumulated,
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
        val startLocal = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val endLocal = Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
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