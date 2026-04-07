package com.drivershield.presentation.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import javax.inject.Inject

data class DayProgressive(
    val dayReport: DayReport,
    val progressiveWeeklyMs: Long
)

data class WeekSummary(
    val isoYear: Int,
    val isoWeek: Int,
    val weekStart: LocalDate,   // Lunes
    val weekEnd: LocalDate,     // Domingo
    val days: List<DayProgressive>,
    val totalWeeklyWorkMs: Long,
    val totalWeeklyRestMs: Long,
    val isCurrentWeek: Boolean
)

/**
 * Modelo de vista para la pantalla de Historial.
 *
 * ## Agrupación semanal [WeekSummary]
 * Los registros crudos de [ShiftRepository] son [DayReport] (un informe por día). Este
 * ViewModel los agrega en semanas ISO (lunes-domingo) para ofrecer la vista requerida por
 * las normativas de registro de jornada (RDL 8/2019 en España, Directiva 2003/88/CE).
 *
 * La agrupación es reactiva: usa [combine] con dos fuentes:
 * 1. `getAllSessionsWithEvents()` — Flow de Room que emite cada vez que hay cambios en DB.
 * 2. `_dateRange` — StateFlow controlado por la UI cuando el usuario filtra por fechas.
 *
 * ## Evidencia inmutable: el campo `isTampered`
 * Cada [DayReport] puede contener sesiones con `isTampered = true`. Este flag, escrito
 * por [TimerService] al detectar un cambio de reloj del sistema, **nunca se borra**.
 * Su presencia en el historial cumple una doble función legal:
 *
 * - **Integridad hacia el trabajador**: demuestra que el registro fue generado en
 *   condiciones anómalas, protegiéndolo de reclamaciones basadas en esos datos.
 * - **Integridad hacia la empresa/Inspección**: evidencia que la app detectó la
 *   anomalía y no la ocultó, mostrando transparencia en la cadena de custodia.
 *
 * ## Carga progresiva semanal
 * [DayProgressive.progressiveWeeklyMs] es el acumulado de horas de trabajo desde el
 * lunes hasta ese día (inclusive), permitiendo a la UI mostrar la barra de progreso
 * semanal sin recalcular en cada recomposición.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    shiftRepository: ShiftRepository
) : ViewModel() {

    private val _dateRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val dateRange: StateFlow<Pair<LocalDate, LocalDate>?> = _dateRange.asStateFlow()

    fun setDateRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = Pair(start, end)
    }

    val historyState: StateFlow<List<WeekSummary>> = combine(
        shiftRepository.getAllSessionsWithEvents(),
        _dateRange
    ) { dayReports, range ->
        if (range == null) return@combine emptyList()

        val weekFields = WeekFields.ISO
        val today = LocalDate.now()
        val currentIsoYear = today.get(weekFields.weekBasedYear())
        val currentIsoWeek = today.get(weekFields.weekOfWeekBasedYear())

        dayReports
            .filter { it.date >= range.first && it.date <= range.second }
            .groupBy { report ->
                val isoYear = report.date.get(weekFields.weekBasedYear())
                val isoWeek = report.date.get(weekFields.weekOfWeekBasedYear())
                Pair(isoYear, isoWeek)
            }
            .map { (weekKey, weekDays) ->
                val (isoYear, isoWeek) = weekKey
                val sortedDays = weekDays.sortedBy { it.date }

                var accumulated = 0L
                val daysProgressive = sortedDays.map { day ->
                    accumulated += day.totalWorkMs
                    DayProgressive(day, accumulated)
                }

                val anyDate = sortedDays.first().date
                val weekStart = anyDate.with(DayOfWeek.MONDAY)
                val weekEnd = weekStart.plusDays(6)

                WeekSummary(
                    isoYear = isoYear,
                    isoWeek = isoWeek,
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    days = daysProgressive,
                    totalWeeklyWorkMs = sortedDays.sumOf { it.totalWorkMs },
                    totalWeeklyRestMs = sortedDays.sumOf { it.totalRestMs },
                    isCurrentWeek = isoYear == currentIsoYear && isoWeek == currentIsoWeek
                )
            }
            .sortedByDescending { it.weekStart }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
