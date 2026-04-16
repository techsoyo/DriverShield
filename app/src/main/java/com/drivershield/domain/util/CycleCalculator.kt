package com.drivershield.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Estado de un día evaluado por el motor de Libranza por Desplazamiento.
 *
 *  LIBRE                 → Día de libranza efectivo.
 *  TRABAJO               → Día laborable normal.
 *  TRABAJO_COMPENSACION  → Día fijo bloqueado durante la Fase de Compensación.
 *                          El conductor trabaja para saldar la "deuda" generada
 *                          por el día extra de la Fase de Convivencia.
 */
enum class DayStatus { LIBRE, TRABAJO, TRABAJO_COMPENSACION }

object CycleCalculator {

    // ══════════════════════════════════════════════════════════════════════
    // Utilidad interna
    // ══════════════════════════════════════════════════════════════════════

    private fun LocalDate.isoMonday(): LocalDate =
        this.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ══════════════════════════════════════════════════════════════════════
    // MOTOR DE LIBRANZA POR DESPLAZAMIENTO
    //
    // Ciclo de N semanas (N×7 días) anclado a un Lunes de referencia.
    //
    // PASO 1 — Normalizar ambas fechas al Lunes ISO de su semana.
    // PASO 2 — daysSinceAnchor = DAYS.between(mondayAnchor, mondayTarget)
    //          Puede ser negativo (fecha anterior al ancla) — floorMod lo corrige.
    // PASO 3 — pos = Math.floorMod(daysSinceAnchor, N×7)
    //          Siempre ∈ [0, N×7-1]. floorMod(-98, 35)=7 vs -98%35=-28 (incorrecto).
    // PASO 4 — weekInCycle = pos / 7  (entero, 0-indexed)
    // PASO 5 — altDay1 = alternateOffDays.minOrNull()  (menor ID → más cercano al Lunes)
    //          altDay2 = alternateOffDays.maxOrNull()  (mayor ID → más cercano al Domingo)
    // PASO 6 — Clasificación por fase:
    //
    //   weekInCycle == 0  (Fase CONVIVENCIA):
    //     LIBRE si dow ∈ fixedOffDays  O  dow == altDay1
    //
    //   weekInCycle == 1  (Fase COMPENSACIÓN):
    //     TRABAJO_COMPENSACION si dow ∈ fixedOffDays   ← bloqueo de deuda
    //     LIBRE si dow == altDay2
    //     TRABAJO en cualquier otro caso
    //
    //   weekInCycle >= 2  (Fase RÉGIMEN):
    //     LIBRE si dow ∈ fixedOffDays
    //     TRABAJO en cualquier otro caso
    //
    // Blindaje nocturno: el LLAMADOR siempre pasa la fecha del startTimestamp,
    // nunca la del endTimestamp. Un turno 22h→02h del martes: el llamador
    // extrae LocalDate(startTimestamp) → Lunes de esa semana → fase correcta.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Motor universal — función pura: (Date, Settings) → DayStatus.
     *
     * @param targetDate       Fecha a evaluar (usar startTimestamp del turno).
     * @param anchorDate       Lunes de inicio del ciclo (nextAltReference del DataStore).
     * @param fixedOffDays     Días de libranza predeterminados (ISO 1=Lun…7=Dom).
     * @param alternateOffDays Días de libranza especiales del ciclo de alternancia.
     * @param weeksToRotate    Duración del bloque de rotación N.
     *
     * Falla seguro: si anchorDate es null, weeksToRotate ≤ 0 o alternateOffDays
     * está vacío, aplica solo fixedOffDays (sin alternancia).
     */
    fun getDayStatus(
        targetDate: LocalDate,
        anchorDate: LocalDate?,
        fixedOffDays: Set<Int>,
        alternateOffDays: Set<Int>,
        weeksToRotate: Int
    ): DayStatus {
        val dow = targetDate.dayOfWeek.value

        if (anchorDate == null || weeksToRotate <= 0 || alternateOffDays.isEmpty()) {
            return if (dow in fixedOffDays) DayStatus.LIBRE else DayStatus.TRABAJO
        }

        val altDay1 = alternateOffDays.minOrNull() ?: return DayStatus.TRABAJO
        val altDay2 = alternateOffDays.maxOrNull() ?: return DayStatus.TRABAJO

        val cycleLengthDays = weeksToRotate * 7L
        val daysSinceAnchor = ChronoUnit.DAYS.between(
            anchorDate.isoMonday(),
            targetDate.isoMonday()
        )
        val pos = Math.floorMod(daysSinceAnchor, cycleLengthDays).toInt()
        val weekInCycle = pos / 7

        return when (weekInCycle) {
            0 -> {
                // Fase CONVIVENCIA: días fijos + altDay1 son LIBRE
                if (dow in fixedOffDays || dow == altDay1) DayStatus.LIBRE
                else DayStatus.TRABAJO
            }
            1 -> {
                // Fase COMPENSACIÓN: días fijos bloqueados, solo altDay2 es LIBRE
                when {
                    dow in fixedOffDays -> DayStatus.TRABAJO_COMPENSACION
                    dow == altDay2      -> DayStatus.LIBRE
                    else                -> DayStatus.TRABAJO
                }
            }
            else -> {
                // Fase RÉGIMEN: solo días fijos son LIBRE
                if (dow in fixedOffDays) DayStatus.LIBRE else DayStatus.TRABAJO
            }
        }
    }

    /**
     * True si [targetDate] es día de libranza efectiva.
     * Blindaje nocturno: el llamador usa la fecha del startTimestamp.
     */
    fun isOffDay(
        targetDate: LocalDate,
        anchorDate: LocalDate?,
        fixedOffDays: Set<Int>,
        alternateOffDays: Set<Int>,
        weeksToRotate: Int
    ): Boolean =
        getDayStatus(targetDate, anchorDate, fixedOffDays, alternateOffDays, weeksToRotate) ==
            DayStatus.LIBRE

    /**
     * Días laborables efectivos en la semana ISO que empieza en [weekStart].
     * TRABAJO_COMPENSACION cuenta como día de trabajo (no como libranza).
     */
    fun getWorkDaysInWeek(
        weekStart: LocalDate,
        anchorDate: LocalDate?,
        fixedOffDays: Set<Int>,
        alternateOffDays: Set<Int>,
        weeksToRotate: Int
    ): Int = 7 - (0..6).count { offset ->
        getDayStatus(
            weekStart.plusDays(offset.toLong()),
            anchorDate, fixedOffDays, alternateOffDays, weeksToRotate
        ) == DayStatus.LIBRE
    }

    /**
     * Fase del ciclo de la semana que empieza en [weekStart]:
     *   0 = CONVIVENCIA
     *   1 = COMPENSACIÓN
     *   2..N-1 = RÉGIMEN
     *
     * Retorna 2 (RÉGIMEN) si anchorDate es null o weeksToRotate ≤ 0.
     */
    fun weekPhase(
        weekStart: LocalDate,
        anchorDate: LocalDate?,
        weeksToRotate: Int
    ): Int {
        if (anchorDate == null || weeksToRotate <= 0) return 2
        val cycleLengthDays = weeksToRotate * 7L
        val daysSinceAnchor = ChronoUnit.DAYS.between(
            anchorDate.isoMonday(),
            weekStart.isoMonday()
        )
        val pos = Math.floorMod(daysSinceAnchor, cycleLengthDays).toInt()
        return pos / 7
    }

    /**
     * Días hasta el inicio de la próxima Fase de Convivencia partiendo de [date].
     * Si la semana actual ya es Convivencia, retorna días hasta la SIGUIENTE (N semanas).
     * Retorna 0 si anchorDate es null o weeksToRotate ≤ 0.
     */
    fun daysUntilNextRotation(
        date: LocalDate,
        anchorDate: LocalDate?,
        weeksToRotate: Int
    ): Long {
        if (anchorDate == null || weeksToRotate <= 0) return 0L
        val cycleLengthDays = weeksToRotate * 7L
        val daysSinceAnchor = ChronoUnit.DAYS.between(
            anchorDate.isoMonday(),
            date.isoMonday()
        )
        val posInCycle = Math.floorMod(daysSinceAnchor, cycleLengthDays)
        val daysUntilNextStart = if (posInCycle == 0L) cycleLengthDays
                                 else cycleLengthDays - posInCycle
        return ChronoUnit.DAYS.between(date, date.isoMonday().plusDays(daysUntilNextStart))
    }
}
