package com.drivershield.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Tests para CycleCalculator — motor de Libranza por Desplazamiento.
 *
 * Configuración estándar usada en todos los tests:
 *   fixedOffDays   = {7}   (Domingo)
 *   alternateOffDays = {5, 6}  → altDay1=5 (Viernes), altDay2=6 (Sábado)
 *   weeksToRotate  = 5
 *
 * El ancla es siempre el Lunes 2026-01-05 (semana ISO 2 de 2026).
 */
class CycleCalculatorTest {

    private val anchor = LocalDate.of(2026, 1, 5) // Lunes
    private val fixed = setOf(7)         // Domingo
    private val alt = setOf(5, 6)        // Viernes y Sábado
    private val weeks = 5

    // Helpers para obtener una fecha concreta de la semana objetivo
    private fun dateInWeek(referenceMonday: LocalDate, dayOfWeek: DayOfWeek): LocalDate =
        referenceMonday.with(TemporalAdjusters.nextOrSame(dayOfWeek))

    // semanas del ciclo desde el ancla
    private val week0Monday = anchor                          // Fase CONVIVENCIA
    private val week1Monday = anchor.plusWeeks(1)             // Fase COMPENSACIÓN
    private val week2Monday = anchor.plusWeeks(2)             // Fase RÉGIMEN
    private val week4Monday = anchor.plusWeeks(4)             // Última semana RÉGIMEN
    private val week5Monday = anchor.plusWeeks(5)             // Vuelta a CONVIVENCIA

    // ─── Fallback sin ancla ──────────────────────────────────────────────

    @Test
    fun `GIVEN null anchorDate WHEN getDayStatus on Sunday THEN LIBRE`() {
        val sunday = LocalDate.of(2026, 4, 12)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(sunday, null, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN null anchorDate WHEN getDayStatus on Monday THEN TRABAJO`() {
        val monday = LocalDate.of(2026, 4, 13)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(monday, null, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN weeksToRotate zero WHEN getDayStatus THEN falls back to fixed days only`() {
        val sunday = week0Monday.plusDays(6)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(sunday, anchor, fixed, alt, weeksToRotate = 0)
        )
    }

    @Test
    fun `GIVEN empty alternateOffDays WHEN getDayStatus THEN falls back to fixed days only`() {
        val friday = dateInWeek(week0Monday, DayOfWeek.FRIDAY)
        // Sin alt days, Viernes debe ser TRABAJO aunque en convivencia sería LIBRE
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(friday, anchor, fixed, emptySet(), weeks)
        )
    }

    // ─── Fase 0: CONVIVENCIA ─────────────────────────────────────────────

    @Test
    fun `GIVEN week 0 WHEN getDayStatus on Sunday (fixed off day) THEN LIBRE`() {
        val sunday = dateInWeek(week0Monday, DayOfWeek.SUNDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(sunday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 0 WHEN getDayStatus on Friday (altDay1) THEN LIBRE`() {
        val friday = dateInWeek(week0Monday, DayOfWeek.FRIDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(friday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 0 WHEN getDayStatus on Saturday (altDay2, not altDay1) THEN TRABAJO`() {
        val saturday = dateInWeek(week0Monday, DayOfWeek.SATURDAY)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(saturday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 0 WHEN getDayStatus on Monday THEN TRABAJO`() {
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(week0Monday, anchor, fixed, alt, weeks)
        )
    }

    // ─── Fase 1: COMPENSACIÓN ────────────────────────────────────────────

    @Test
    fun `GIVEN week 1 WHEN getDayStatus on Sunday (fixed off day) THEN TRABAJO_COMPENSACION`() {
        val sunday = dateInWeek(week1Monday, DayOfWeek.SUNDAY)
        assertEquals(
            DayStatus.TRABAJO_COMPENSACION,
            CycleCalculator.getDayStatus(sunday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 1 WHEN getDayStatus on Saturday (altDay2) THEN LIBRE`() {
        val saturday = dateInWeek(week1Monday, DayOfWeek.SATURDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(saturday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 1 WHEN getDayStatus on Friday (altDay1, not altDay2) THEN TRABAJO`() {
        val friday = dateInWeek(week1Monday, DayOfWeek.FRIDAY)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(friday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 1 WHEN getDayStatus on Wednesday THEN TRABAJO`() {
        val wednesday = dateInWeek(week1Monday, DayOfWeek.WEDNESDAY)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(wednesday, anchor, fixed, alt, weeks)
        )
    }

    // ─── Fase 2+: RÉGIMEN ────────────────────────────────────────────────

    @Test
    fun `GIVEN week 2 WHEN getDayStatus on Sunday (fixed off day) THEN LIBRE`() {
        val sunday = dateInWeek(week2Monday, DayOfWeek.SUNDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(sunday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 2 WHEN getDayStatus on Friday (alt day) THEN TRABAJO in REGIMEN`() {
        val friday = dateInWeek(week2Monday, DayOfWeek.FRIDAY)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(friday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 4 (last REGIMEN week) WHEN getDayStatus on Sunday THEN LIBRE`() {
        val sunday = dateInWeek(week4Monday, DayOfWeek.SUNDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(sunday, anchor, fixed, alt, weeks)
        )
    }

    // ─── Ciclicidad ──────────────────────────────────────────────────────

    @Test
    fun `GIVEN week 5 (start of next cycle) WHEN getDayStatus on Friday THEN LIBRE (back to CONVIVENCIA)`() {
        val friday = dateInWeek(week5Monday, DayOfWeek.FRIDAY)
        assertEquals(
            DayStatus.LIBRE,
            CycleCalculator.getDayStatus(friday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN date before anchor WHEN getDayStatus THEN floorMod handles negative offset correctly`() {
        // Una semana antes del ancla: debe ser la última semana del ciclo anterior (RÉGIMEN)
        val prevFriday = dateInWeek(anchor.minusWeeks(1), DayOfWeek.FRIDAY)
        assertEquals(
            DayStatus.TRABAJO,
            CycleCalculator.getDayStatus(prevFriday, anchor, fixed, alt, weeks)
        )
    }

    // ─── isOffDay ────────────────────────────────────────────────────────

    @Test
    fun `GIVEN week 0 Friday WHEN isOffDay THEN true`() {
        val friday = dateInWeek(week0Monday, DayOfWeek.FRIDAY)
        assertTrue(CycleCalculator.isOffDay(friday, anchor, fixed, alt, weeks))
    }

    @Test
    fun `GIVEN week 0 Saturday WHEN isOffDay THEN false`() {
        val saturday = dateInWeek(week0Monday, DayOfWeek.SATURDAY)
        assertFalse(CycleCalculator.isOffDay(saturday, anchor, fixed, alt, weeks))
    }

    // ─── getWorkDaysInWeek ───────────────────────────────────────────────

    @Test
    fun `GIVEN week 0 WHEN getWorkDaysInWeek THEN 5 days (Domingo+Viernes=libre, Sabado=trabajo)`() {
        // Libres: Domingo (fixed), Viernes (altDay1) → 2 libres → 5 trabajo
        assertEquals(
            5,
            CycleCalculator.getWorkDaysInWeek(week0Monday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 1 WHEN getWorkDaysInWeek THEN 6 days (only Saturday=LIBRE, Sunday=TRABAJO_COMPENSACION counts as work)`() {
        // LIBRE: solo Sábado (altDay2) → 1 libre → 6 trabajo
        assertEquals(
            6,
            CycleCalculator.getWorkDaysInWeek(week1Monday, anchor, fixed, alt, weeks)
        )
    }

    @Test
    fun `GIVEN week 2 WHEN getWorkDaysInWeek THEN 6 days (only Sunday=LIBRE)`() {
        // LIBRE: solo Domingo → 1 libre → 6 trabajo
        assertEquals(
            6,
            CycleCalculator.getWorkDaysInWeek(week2Monday, anchor, fixed, alt, weeks)
        )
    }

    // ─── weekPhase ───────────────────────────────────────────────────────

    @Test
    fun `GIVEN week 0 monday WHEN weekPhase THEN 0 (CONVIVENCIA)`() {
        assertEquals(0, CycleCalculator.weekPhase(week0Monday, anchor, weeks))
    }

    @Test
    fun `GIVEN week 1 monday WHEN weekPhase THEN 1 (COMPENSACION)`() {
        assertEquals(1, CycleCalculator.weekPhase(week1Monday, anchor, weeks))
    }

    @Test
    fun `GIVEN week 2 monday WHEN weekPhase THEN 2 (REGIMEN)`() {
        assertEquals(2, CycleCalculator.weekPhase(week2Monday, anchor, weeks))
    }

    @Test
    fun `GIVEN week 5 monday (new cycle) WHEN weekPhase THEN 0 (CONVIVENCIA again)`() {
        assertEquals(0, CycleCalculator.weekPhase(week5Monday, anchor, weeks))
    }

    @Test
    fun `GIVEN null anchorDate WHEN weekPhase THEN 2 (defaults to REGIMEN)`() {
        assertEquals(2, CycleCalculator.weekPhase(week0Monday, null, weeks))
    }
}
