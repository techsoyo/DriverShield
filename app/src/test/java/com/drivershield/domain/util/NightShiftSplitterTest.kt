package com.drivershield.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Tests unitarios para NightShiftSplitter.
 *
 * Todos los timestamps se expresan en UTC (ZoneOffset.UTC) para que los
 * resultados sean deterministas independientemente de la zona horaria
 * de la máquina que ejecute la suite.
 */
class NightShiftSplitterTest {

    private val zone = ZoneOffset.UTC

    /** Convierte "2026-04-16T22:00:00Z" en epoch-millis. */
    private fun epochMs(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date)
            .atTime(hour, minute)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    // ─── Test 1 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN turno nocturno 22h a 02h WHEN msPerDay THEN 2h en dia1 y 2h en dia2`() {
        // GIVEN
        val startMs = epochMs("2026-04-16", 22)   // Jueves 22:00 UTC
        val endMs   = epochMs("2026-04-17", 2)    // Viernes  02:00 UTC

        // WHEN
        val result = NightShiftSplitter.msPerDay(startMs, endMs, zone)

        // THEN
        val day1 = LocalDate.parse("2026-04-16")
        val day2 = LocalDate.parse("2026-04-17")
        val twoHoursMs = 2L * 60 * 60 * 1000

        assertEquals(2, result.size, "Deben existir exactamente 2 entradas en el mapa")
        assertEquals(twoHoursMs, result[day1], "El jueves debe acumular exactamente 2 h")
        assertEquals(twoHoursMs, result[day2], "El viernes debe acumular exactamente 2 h")
    }

    // ─── Test 2 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN turno diurno mismo dia 08h a 16h WHEN msPerDay THEN entrada unica con 8h`() {
        // GIVEN
        val startMs = epochMs("2026-04-16", 8)
        val endMs   = epochMs("2026-04-16", 16)

        // WHEN
        val result = NightShiftSplitter.msPerDay(startMs, endMs, zone)

        // THEN
        val day = LocalDate.parse("2026-04-16")
        val eightHoursMs = 8L * 60 * 60 * 1000

        assertEquals(1, result.size, "Solo debe existir un día en el mapa")
        assertEquals(eightHoursMs, result[day], "Deben ser exactamente 8 h")
    }

    // ─── Test 3 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN turno que cruza dos medianoche WHEN msPerDay THEN tres entradas correctas`() {
        // GIVEN: turno de 54 horas (Lunes 06:00 → Miércoles 12:00)
        val startMs = epochMs("2026-04-13", 6)    // Lunes 06:00
        val endMs   = epochMs("2026-04-15", 12)   // Miércoles 12:00

        // WHEN
        val result = NightShiftSplitter.msPerDay(startMs, endMs, zone)

        // THEN
        val monday    = LocalDate.parse("2026-04-13")
        val tuesday   = LocalDate.parse("2026-04-14")
        val wednesday = LocalDate.parse("2026-04-15")

        assertEquals(3, result.size, "Deben existir 3 entradas (lunes, martes, miércoles)")
        assertEquals(18L * 3_600_000L, result[monday],    "Lunes: 18 h (06:00–00:00)")
        assertEquals(24L * 3_600_000L, result[tuesday],   "Martes: 24 h completas")
        assertEquals(12L * 3_600_000L, result[wednesday], "Miércoles: 12 h (00:00–12:00)")
    }

    // ─── Test 4 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN startMs igual a endMs WHEN msPerDay THEN mapa vacio`() {
        // GIVEN
        val ms = epochMs("2026-04-16", 10)

        // WHEN
        val result = NightShiftSplitter.msPerDay(ms, ms, zone)

        // THEN
        assertTrue(result.isEmpty(), "Un intervalo de duración cero no debe generar entradas")
    }

    // ─── Test 5 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN turno que termina exactamente a medianoche WHEN msPerDay THEN solo entrada del dia1`() {
        // GIVEN: turno de 2 horas que termina exactamente en medianoche
        val startMs = epochMs("2026-04-16", 22)   // 22:00
        val endMs   = epochMs("2026-04-17", 0)    // 00:00 del día siguiente

        // WHEN
        val result = NightShiftSplitter.msPerDay(startMs, endMs, zone)

        // THEN
        val day1 = LocalDate.parse("2026-04-16")
        val twoHoursMs = 2L * 60 * 60 * 1000

        assertEquals(1, result.size, "No debe haber entrada para el día 2 ya que el fin es exactamente medianoche")
        assertEquals(twoHoursMs, result[day1])
    }
}
