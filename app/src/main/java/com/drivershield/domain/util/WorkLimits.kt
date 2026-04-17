package com.drivershield.domain.util

/**
 * Constantes y validadores de los límites legales de jornada.
 *
 * Fuentes normativas:
 * - RDL 8/2019: máximo 8 horas diarias de trabajo efectivo.
 * - Directiva 2003/88/CE: mínimo 4 horas de descanso entre turnos.
 *
 * [AlertScheduler] usa estas constantes para programar las alarmas a 4 h, 6 h y 8 h.
 */
object WorkLimits {

    /** Límite diario de trabajo efectivo: 8 h en milisegundos (RDL 8/2019). */
    const val MAX_WORK_DAY_MS = 8L * 60 * 60 * 1000

    /** Descanso mínimo entre turnos: 4 h en milisegundos (Directiva 2003/88/CE). */
    const val MAX_REST_SHIFT_MS = 4L * 60 * 60 * 1000

    /**
     * @param workMs Milisegundos de trabajo acumulado en el día.
     * @return `true` si se supera el límite de 8 h.
     */
    fun hasWorkDayExcess(workMs: Long): Boolean = workMs > MAX_WORK_DAY_MS

    /**
     * @param restMs Milisegundos de descanso acumulado en el turno.
     * @return `true` si se supera el máximo de 4 h de descanso continuo.
     */
    fun hasRestShiftExcess(restMs: Long): Boolean = restMs > MAX_REST_SHIFT_MS
}
