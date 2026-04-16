package com.drivershield.domain.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkLimitsTest {

    // ─── hasWorkDayExcess ────────────────────────────────────────────────

    @Test
    fun `GIVEN work time exactly at 8h limit WHEN hasWorkDayExcess THEN false`() {
        assertFalse(WorkLimits.hasWorkDayExcess(WorkLimits.MAX_WORK_DAY_MS))
    }

    @Test
    fun `GIVEN work time 1ms over 8h limit WHEN hasWorkDayExcess THEN true`() {
        assertTrue(WorkLimits.hasWorkDayExcess(WorkLimits.MAX_WORK_DAY_MS + 1L))
    }

    @Test
    fun `GIVEN work time 1ms under 8h limit WHEN hasWorkDayExcess THEN false`() {
        assertFalse(WorkLimits.hasWorkDayExcess(WorkLimits.MAX_WORK_DAY_MS - 1L))
    }

    @Test
    fun `GIVEN zero work time WHEN hasWorkDayExcess THEN false`() {
        assertFalse(WorkLimits.hasWorkDayExcess(0L))
    }

    @Test
    fun `GIVEN very large work time WHEN hasWorkDayExcess THEN true`() {
        assertTrue(WorkLimits.hasWorkDayExcess(Long.MAX_VALUE))
    }

    // ─── hasRestShiftExcess ──────────────────────────────────────────────

    @Test
    fun `GIVEN rest time exactly at 4h limit WHEN hasRestShiftExcess THEN false`() {
        assertFalse(WorkLimits.hasRestShiftExcess(WorkLimits.MAX_REST_SHIFT_MS))
    }

    @Test
    fun `GIVEN rest time 1ms over 4h limit WHEN hasRestShiftExcess THEN true`() {
        assertTrue(WorkLimits.hasRestShiftExcess(WorkLimits.MAX_REST_SHIFT_MS + 1L))
    }

    @Test
    fun `GIVEN rest time 1ms under 4h limit WHEN hasRestShiftExcess THEN false`() {
        assertFalse(WorkLimits.hasRestShiftExcess(WorkLimits.MAX_REST_SHIFT_MS - 1L))
    }

    @Test
    fun `GIVEN zero rest time WHEN hasRestShiftExcess THEN false`() {
        assertFalse(WorkLimits.hasRestShiftExcess(0L))
    }

    // ─── Constants sanity ───────────────────────────────────────────────

    @Test
    fun `GIVEN constants WHEN checked THEN MAX_WORK_DAY_MS is 8 hours in ms`() {
        org.junit.jupiter.api.Assertions.assertEquals(
            8L * 60 * 60 * 1000,
            WorkLimits.MAX_WORK_DAY_MS
        )
    }

    @Test
    fun `GIVEN constants WHEN checked THEN MAX_REST_SHIFT_MS is 4 hours in ms`() {
        org.junit.jupiter.api.Assertions.assertEquals(
            4L * 60 * 60 * 1000,
            WorkLimits.MAX_REST_SHIFT_MS
        )
    }
}
