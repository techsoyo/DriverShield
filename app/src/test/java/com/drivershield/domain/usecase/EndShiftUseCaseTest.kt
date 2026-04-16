package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.domain.util.WorkLimits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EndShiftUseCaseTest {

    private lateinit var repository: ShiftRepository
    private lateinit var useCase: EndShiftUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = EndShiftUseCase(repository)
    }

    @Test
    fun `GIVEN active session id WHEN invoke THEN delegates to repository and returns closed session`() =
        runBlocking {
            // GIVEN
            val startTime = Instant.parse("2026-04-16T06:00:00Z")
            val endTime = Instant.parse("2026-04-16T14:00:00Z")
            val expected = ShiftSession(
                id = 1L,
                type = ShiftType.NORMAL,
                startTime = startTime,
                endTime = endTime
            )
            coEvery { repository.endSession(1L) } returns expected

            // WHEN
            val result = useCase(1L)

            // THEN
            assertEquals(expected, result)
            coVerify(exactly = 1) { repository.endSession(1L) }
        }

    @Test
    fun `GIVEN session id WHEN invoke THEN returned session has endTime set (not active)`() =
        runBlocking {
            // GIVEN
            val closedSession = ShiftSession(
                id = 5L,
                type = ShiftType.EXTENDED,
                startTime = Instant.parse("2026-04-16T20:00:00Z"),
                endTime = Instant.parse("2026-04-17T04:00:00Z")
            )
            coEvery { repository.endSession(5L) } returns closedSession

            // WHEN
            val result = useCase(5L)

            // THEN
            assertNotNull(result.endTime)
            assertNull(result.durationMillis?.let { if (it < 0) it else null })
        }

    @Test
    fun `GIVEN session with 8h duration WHEN invoke THEN durationMillis equals 8h in ms`() =
        runBlocking {
            // GIVEN
            val start = Instant.parse("2026-04-16T06:00:00Z")
            val end = Instant.parse("2026-04-16T14:00:00Z")
            val session = ShiftSession(id = 2L, startTime = start, endTime = end)
            coEvery { repository.endSession(2L) } returns session

            // WHEN
            val result = useCase(2L)

            // THEN
            assertEquals(8L * 60 * 60 * 1000, result.durationMillis)
        }

    @Test
    fun `GIVEN session with exactly 8h 0ms WHEN durationMillis compared to MAX_WORK_DAY_MS THEN not exceeded`() =
        runBlocking {
            // GIVEN
            val start = Instant.parse("2026-04-16T06:00:00Z")
            val end = start.plusMillis(WorkLimits.MAX_WORK_DAY_MS)
            val session = ShiftSession(id = 3L, startTime = start, endTime = end)
            coEvery { repository.endSession(3L) } returns session

            // WHEN
            val result = useCase(3L)

            // THEN — límite exacto: no debe detectarse exceso
            assertEquals(false, WorkLimits.hasWorkDayExcess(result.durationMillis ?: 0L))
        }

    @Test
    fun `GIVEN session 1ms over 8h WHEN durationMillis compared to MAX_WORK_DAY_MS THEN exceeded`() =
        runBlocking {
            // GIVEN
            val start = Instant.parse("2026-04-16T06:00:00Z")
            val end = start.plusMillis(WorkLimits.MAX_WORK_DAY_MS + 1L)
            val session = ShiftSession(id = 4L, startTime = start, endTime = end)
            coEvery { repository.endSession(4L) } returns session

            // WHEN
            val result = useCase(4L)

            // THEN — 1ms sobre el límite: debe detectarse exceso
            assertEquals(true, WorkLimits.hasWorkDayExcess(result.durationMillis ?: 0L))
        }

    @Test
    fun `GIVEN invoke called WHEN finished THEN repository called exactly once`() = runBlocking {
        // GIVEN
        coEvery { repository.endSession(any()) } returns ShiftSession(id = 99L)

        // WHEN
        useCase(99L)

        // THEN
        coVerify(exactly = 1) { repository.endSession(99L) }
    }
}
