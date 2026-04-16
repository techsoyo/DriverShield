package com.drivershield.domain.usecase

import com.drivershield.domain.model.DailyReport
import com.drivershield.domain.model.DayOverride
import com.drivershield.domain.model.WeeklyReport
import com.drivershield.domain.repository.DayOverrideRepository
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.domain.util.NightShiftSplitter
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import javax.inject.Inject

/**
 * Genera informes legales de jornada agrupados por semana ISO.
 *
 * ## Invariante legal crítica
 * Un turno nocturno (p. ej. Domingo 22:00 → Lunes 06:00) se redistribuye
 * proporcionalmente entre los días naturales que abarca mediante
 * [NightShiftSplitter.msPerDay]. Así, las 2h del domingo van a la semana ISO
 * del domingo y las 6h del lunes van a la semana ISO del lunes. Cero ms
 * se pierden en los cortes de medianoche.
 *
 * ## Cálculo de targetWorkMs
 * Para cada semana del informe:
 *   `targetWorkMs = díasLaborablesEfectivos × WorkSchedule.dailyTargetMs`
 *
 * Un día es laborable si:
 *   1. No está en [WorkSchedule.offDays] (días fijos de libranza), Y
 *   2. No tiene un [DayOverride] con `isLibranza = true`.
 *
 * El override siempre tiene prioridad sobre el día fijo (en ambas direcciones:
 * puede convertir un día fijo en laboral y viceversa).
 *
 * @param shiftRepository       Fuente de sesiones de turno con eventos.
 * @param scheduleRepository    Horario configurado por el conductor.
 * @param dayOverrideRepository Overrides manuales del calendario.
 */
class GenerateReportUseCase @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val scheduleRepository: ScheduleRepository,
    private val dayOverrideRepository: DayOverrideRepository
) {

    /**
     * Genera la lista de [WeeklyReport] para el rango [start, end] (ambos inclusive).
     *
     * @param start Primer día del rango.
     * @param end   Último día del rango. No puede ser anterior a [start].
     * @param zone  Zona horaria para determinar los límites de cada día natural.
     *              Usar [ZoneOffset.UTC] en tests para resultados deterministas.
     */
    suspend operator fun invoke(
        start: LocalDate,
        end: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WeeklyReport> {
        require(!start.isAfter(end)) { "start ($start) must not be after end ($end)" }

        // 1. Recoger datos en paralelo (suspending)
        val allDayReports = shiftRepository.getAllSessionsWithEvents().first()
        val overrides = dayOverrideRepository.getOverridesInRange(start, end).first()
        val schedule = scheduleRepository.getSchedule()

        val overrideMap: Map<LocalDate, DayOverride> = overrides.associateBy { it.date }
        val offDays: Set<Int> = schedule?.offDays?.toSet() ?: emptySet()
        val dailyTargetMs: Long = schedule?.dailyTargetMs ?: 0L

        // 2. Redistribuir ms de cada sesión cerrada por día natural
        val workPerDay = mutableMapOf<LocalDate, Long>()
        val restPerDay = mutableMapOf<LocalDate, Long>()

        allDayReports
            .flatMap { it.sessions }
            .filter { it.endTimestamp != null }
            .forEach { session ->
                val endTs = session.endTimestamp ?: return@forEach
                val wallClockMs = endTs - session.startTimestamp
                if (wallClockMs <= 0L) return@forEach

                // Distribuir proporcionalmente: workShare/day = dayWallMs * totalWorkMs / wallClockMs
                NightShiftSplitter.msPerDay(session.startTimestamp, endTs, zone)
                    .forEach { (date, dayWallMs) ->
                        val workShare = dayWallMs * session.totalWorkMs / wallClockMs
                        val restShare = dayWallMs * session.totalRestMs / wallClockMs
                        workPerDay[date] = (workPerDay[date] ?: 0L) + workShare
                        restPerDay[date] = (restPerDay[date] ?: 0L) + restShare
                    }
            }

        // 3. Construir un DailyReport por cada fecha del rango solicitado
        val dailyReports = buildList<DailyReport> {
            var current = start
            while (!current.isAfter(end)) {
                add(
                    DailyReport(
                        date = current,
                        totalWorkMs = workPerDay[current] ?: 0L,
                        totalRestMs = restPerDay[current] ?: 0L,
                        isLibranza = isLibranzaDay(current, offDays, overrideMap[current])
                    )
                )
                current = current.plusDays(1L)
            }
        }

        // 4. Agrupar por semana ISO y construir WeeklyReport
        val weekFields = WeekFields.ISO
        return dailyReports
            .groupBy { day ->
                Pair(
                    day.date.get(weekFields.weekBasedYear()),
                    day.date.get(weekFields.weekOfWeekBasedYear())
                )
            }
            .map { (weekKey, days) ->
                val (isoYear, isoWeek) = weekKey
                val monday = days.first().date
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6L)

                // Contar solo los días del rango que son laborables
                val workDayCount = days.count { day ->
                    !isLibranzaDay(day.date, offDays, overrideMap[day.date])
                }

                WeeklyReport(
                    isoYear = isoYear,
                    weekNumber = isoWeek,
                    startDate = monday,
                    endDate = sunday,
                    days = days.sortedBy { it.date },
                    totalWeeklyWorkMs = days.sumOf { it.totalWorkMs },
                    targetWorkMs = workDayCount.toLong() * dailyTargetMs
                )
            }
            .sortedBy { it.startDate }
    }

    /**
     * Determina si [date] es día de libranza planificado.
     *
     * Prioridad: override manual > día fijo de [WorkSchedule].
     */
    private fun isLibranzaDay(
        date: LocalDate,
        offDays: Set<Int>,
        override: DayOverride?
    ): Boolean = when {
        override != null -> override.isLibranza
        else -> date.dayOfWeek.value in offDays
    }
}
