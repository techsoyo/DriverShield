package com.drivershield.domain.usecase

import com.drivershield.domain.model.DayOverride
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.SessionReport
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.repository.DayOverrideRepository
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GenerateReportUseCaseTest {

    private lateinit var shiftRepository: ShiftRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var dayOverrideRepository: DayOverrideRepository
    private lateinit var useCase: GenerateReportUseCase

    // ZoneOffset.UTC garantiza resultados deterministas independientemente de
    // la zona horaria de la máquina de CI.
    private val zone = ZoneOffset.UTC

    private fun epochMs(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun makeSession(
        id: Long,
        startDate: String,
        startHour: Int,
        endDate: String,
        endHour: Int,
        totalWorkMs: Long,
        totalRestMs: Long = 0L
    ): SessionReport = SessionReport(
        sessionId = id,
        startTimestamp = epochMs(startDate, startHour),
        endTimestamp = epochMs(endDate, endHour),
        events = emptyList(),
        totalWorkMs = totalWorkMs,
        totalRestMs = totalRestMs
    )

    private fun defaultSchedule(
        offDays: List<Int> = listOf(6, 7),           // Sábado=6, Domingo=7
        dailyTargetMs: Long = 8L * 60 * 60 * 1000   // 8h
    ) = WorkSchedule(
        startTime = "08:00",
        endTime = "16:00",
        offDays = offDays,
        weeklyTargetMs = dailyTargetMs * (7 - offDays.size),
        dailyTargetMs = dailyTargetMs
    )

    @BeforeEach
    fun setUp() {
        shiftRepository = mockk()
        scheduleRepository = mockk()
        dayOverrideRepository = mockk()
        useCase = GenerateReportUseCase(shiftRepository, scheduleRepository, dayOverrideRepository)

        // Defaults que cada test puede sobreescribir
        every { dayOverrideRepository.getOverridesInRange(any(), any()) } returns flowOf(emptyList())
        coEvery { scheduleRepository.getSchedule() } returns defaultSchedule()
    }

    // ─── Test A — Cruce de Semana ─────────────────────────────────────────
    // Invariante crítica: un turno Dom 22:00 → Lun 06:00 (8h) debe repartir
    // 2h a la semana ISO 16 (Domingo) y 6h a la semana ISO 17 (Lunes).
    // Cero ms perdidos en el corte de medianoche dominical.

    @Test
    fun `GIVEN session crossing Sunday to Monday WHEN invoke THEN ms split across two ISO weeks with zero loss`() =
        runBlocking {
            // GIVEN: sesión Domingo 2026-04-19 22:00 → Lunes 2026-04-20 06:00 (8h exactas)
            val totalWorkMs = 8L * 60 * 60 * 1000
            val session = makeSession(
                id = 1L,
                startDate = "2026-04-19", startHour = 22,
                endDate = "2026-04-20", endHour = 6,
                totalWorkMs = totalWorkMs
            )
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(
                listOf(DayReport(date = LocalDate.parse("2026-04-19"), sessions = listOf(session)))
            )

            // WHEN
            val reports = useCase(
                LocalDate.parse("2026-04-13"),
                LocalDate.parse("2026-04-26"),
                zone
            )

            // THEN
            assertEquals(2, reports.size, "Debe haber exactamente 2 semanas ISO")
            val week16 = reports.first { it.weekNumber == 16 }
            val week17 = reports.first { it.weekNumber == 17 }

            val sundayMs = 2L * 60 * 60 * 1000   // 22:00 → 00:00
            val mondayMs = 6L * 60 * 60 * 1000   // 00:00 → 06:00

            assertEquals(sundayMs, week16.totalWeeklyWorkMs,
                "Semana 16 (Domingo): debe acumular exactamente 2h")
            assertEquals(mondayMs, week17.totalWeeklyWorkMs,
                "Semana 17 (Lunes): debe acumular exactamente 6h")
            assertEquals(
                totalWorkMs,
                week16.totalWeeklyWorkMs + week17.totalWeeklyWorkMs,
                "Cero ms perdidos en el corte de medianoche del domingo"
            )
        }

    // ─── Test B — Integridad Mensual ──────────────────────────────────────
    // La suma de todos los WeeklyReport.totalWeeklyWorkMs debe coincidir
    // exactamente con la suma de session.totalWorkMs originales.

    @Test
    fun `GIVEN 10 diurnal sessions in April 2026 WHEN invoke THEN sum of weekly totals equals sum of all session ms`() =
        runBlocking {
            // GIVEN: 10 sesiones diurnas de 8h en días laborables (no cruzan medianoche)
            val eightHoursMs = 8L * 3_600_000L
            val sessions = listOf(
                makeSession(1,  "2026-04-01", 8, "2026-04-01", 16, eightHoursMs),
                makeSession(2,  "2026-04-06", 8, "2026-04-06", 16, eightHoursMs),
                makeSession(3,  "2026-04-07", 8, "2026-04-07", 16, eightHoursMs),
                makeSession(4,  "2026-04-08", 8, "2026-04-08", 16, eightHoursMs),
                makeSession(5,  "2026-04-09", 8, "2026-04-09", 16, eightHoursMs),
                makeSession(6,  "2026-04-13", 8, "2026-04-13", 16, eightHoursMs),
                makeSession(7,  "2026-04-14", 8, "2026-04-14", 16, eightHoursMs),
                makeSession(8,  "2026-04-20", 8, "2026-04-20", 16, eightHoursMs),
                makeSession(9,  "2026-04-27", 8, "2026-04-27", 16, eightHoursMs),
                makeSession(10, "2026-04-28", 8, "2026-04-28", 16, eightHoursMs)
            )
            val dayReports = sessions.map { s ->
                DayReport(
                    date = Instant.ofEpochMilli(s.startTimestamp).atZone(ZoneOffset.UTC).toLocalDate(),
                    sessions = listOf(s)
                )
            }
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(dayReports)

            // WHEN
            val reports = useCase(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"), zone)

            // THEN
            val expectedTotal = sessions.sumOf { it.totalWorkMs }
            val actualTotal = reports.sumOf { it.totalWeeklyWorkMs }
            assertEquals(
                expectedTotal,
                actualTotal,
                "Cero ms perdidos: la suma semanal debe coincidir con la suma de todas las sesiones"
            )
        }

    // ─── Test C — Día fijo de libranza sin sesiones ───────────────────────

    @Test
    fun `GIVEN no sessions on fixed off day WHEN invoke THEN DailyReport isLibranza true and totalWorkMs zero`() =
        runBlocking {
            // GIVEN: Sábado 2026-04-18 (dow=6) en offDays=[6,7], sin sesiones
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(emptyList())
            val saturday = LocalDate.parse("2026-04-18")

            // WHEN
            val reports = useCase(saturday, saturday, zone)

            // THEN
            val day = reports.first().days.first()
            assertTrue(day.isLibranza, "Sábado (día fijo) sin sesiones debe ser isLibranza=true")
            assertEquals(0L, day.totalWorkMs)
            assertEquals(0L, day.totalRestMs)
        }

    // ─── Test D — Sesión activa (endTimestamp null) ignorada ──────────────

    @Test
    fun `GIVEN active session with null endTimestamp WHEN invoke THEN session contributes zero ms`() =
        runBlocking {
            // GIVEN: sesión sin cerrar — no debe aparecer en el informe
            val activeSession = SessionReport(
                sessionId = 99L,
                startTimestamp = epochMs("2026-04-16", 8),
                endTimestamp = null,
                events = emptyList(),
                totalWorkMs = 4L * 60 * 60 * 1000,
                totalRestMs = 0L
            )
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(
                listOf(DayReport(date = LocalDate.parse("2026-04-16"), sessions = listOf(activeSession)))
            )

            // WHEN
            val reports = useCase(
                LocalDate.parse("2026-04-16"),
                LocalDate.parse("2026-04-16"),
                zone
            )

            // THEN
            val day = reports.first().days.first()
            assertEquals(0L, day.totalWorkMs, "Sesión activa no debe contribuir al informe")
            assertEquals(0L, day.totalRestMs)
        }

    // ─── Test E — Sesión REST va a totalRestMs, no a totalWorkMs ─────────

    @Test
    fun `GIVEN session with only rest hours WHEN invoke THEN totalRestMs correct and totalWorkMs zero`() =
        runBlocking {
            // GIVEN: sesión de descanso 12:00–16:00 (4h rest, 0h work)
            val restSession = makeSession(
                id = 1L,
                startDate = "2026-04-16", startHour = 12,
                endDate = "2026-04-16", endHour = 16,
                totalWorkMs = 0L,
                totalRestMs = 4L * 60 * 60 * 1000
            )
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(
                listOf(DayReport(date = LocalDate.parse("2026-04-16"), sessions = listOf(restSession)))
            )

            // WHEN
            val reports = useCase(
                LocalDate.parse("2026-04-16"),
                LocalDate.parse("2026-04-16"),
                zone
            )

            // THEN
            val day = reports.first().days.first()
            assertEquals(0L, day.totalWorkMs, "Sesión REST: totalWorkMs debe ser 0")
            assertEquals(4L * 60 * 60 * 1000, day.totalRestMs, "Sesión REST: totalRestMs debe ser 4h")
        }

    // ─── Test F — targetWorkMs con libranzas fijas + override manual ──────
    // Semana ISO 16 (Apr 13-19, 2026):
    //   offDays fijos: Sábado (6) + Domingo (7) → 5 días potencialmente laborables
    //   Override manual: Miércoles 15/04 → isLibranza=true
    //   Días laborables efectivos: 5 - 1 = 4
    //   targetWorkMs = 4 × 8h = 32h = 115_200_000 ms

    @Test
    fun `GIVEN week with 2 fixed off days and 1 manual libranza override WHEN invoke THEN targetWorkMs equals 4 days work`() =
        runBlocking {
            // GIVEN
            every { shiftRepository.getAllSessionsWithEvents() } returns flowOf(emptyList())
            every { dayOverrideRepository.getOverridesInRange(any(), any()) } returns flowOf(
                listOf(DayOverride(date = LocalDate.parse("2026-04-15"), isLibranza = true))
            )

            // WHEN
            val reports = useCase(
                LocalDate.parse("2026-04-13"),
                LocalDate.parse("2026-04-19"),
                zone
            )

            // THEN
            assertEquals(1, reports.size, "Debe haber exactamente 1 semana ISO")
            val week = reports.first()
            assertEquals(16, week.weekNumber, "Debe ser la semana ISO 16")
            val expectedTargetMs = 4L * 8L * 60 * 60 * 1000   // 32h
            assertEquals(
                expectedTargetMs,
                week.targetWorkMs,
                "targetWorkMs debe ser 4 días laborables × 8h = 32h " +
                    "(2 fijos [Sáb+Dom] + 1 manual [Mié] = 3 libranzas → 4 laborables)"
            )
        }
}
