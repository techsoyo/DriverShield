package com.drivershield.domain.model

import java.time.LocalDate

/**
 * Informe legal agregado de un día natural.
 *
 * Distinto de [DayReport] (historial por sesiones con eventos), [DailyReport]
 * contiene las horas ya redistribuidas por [NightShiftSplitter]: las horas de
 * un turno nocturno quedan imputadas al día natural que corresponde, no al
 * día de inicio de la sesión.
 *
 * @param date        Fecha del día natural.
 * @param totalWorkMs Milisegundos de trabajo efectivo computados en este día.
 * @param totalRestMs Milisegundos de descanso computados en este día.
 * @param isLibranza  True si el día está planificado como libre según el
 *                    calendario configurado (días fijos u override manual).
 *                    No depende de si el conductor trabajó realmente ese día.
 */
data class DailyReport(
    val date: LocalDate,
    val totalWorkMs: Long,
    val totalRestMs: Long,
    val isLibranza: Boolean
)
