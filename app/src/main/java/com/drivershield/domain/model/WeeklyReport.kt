package com.drivershield.domain.model

import java.time.LocalDate

/**
 * Informe legal agregado de una semana ISO (Lunes 00:00 – Domingo 23:59).
 *
 * Agrupa los [DailyReport] de una semana ISO y compara las horas reales
 * trabajadas con el objetivo planificado ("Horas Reales vs. Horas Previstas"),
 * según requieren las inspecciones de jornada (RDL 8/2019, Dir. 2003/88/CE).
 *
 * @param isoYear           Año ISO al que pertenece la semana.
 * @param weekNumber        Número de semana ISO (1–53).
 * @param startDate         Lunes de la semana.
 * @param endDate           Domingo de la semana.
 * @param days              Días incluidos en el rango de consulta, ordenados ASC.
 * @param totalWeeklyWorkMs Suma de [DailyReport.totalWorkMs] de todos los días.
 * @param targetWorkMs      Objetivo de horas para la semana: días laborables
 *                          efectivos × [WorkSchedule.dailyTargetMs]. Los días
 *                          laborables se calculan excluyendo [WorkSchedule.offDays]
 *                          y los overrides de libranza manual ([DayOverride]).
 *                          Es 0L si no hay horario configurado.
 */
data class WeeklyReport(
    val isoYear: Int,
    val weekNumber: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<DailyReport>,
    val totalWeeklyWorkMs: Long,
    val targetWorkMs: Long
)
