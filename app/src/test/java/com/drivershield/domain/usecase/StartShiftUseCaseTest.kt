package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.repository.ShiftRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartShiftUseCaseTest {

    private lateinit var repository: ShiftRepository
    private lateinit var useCase: StartShiftUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = StartShiftUseCase(repository)
    }

    @Test
    fun `GIVEN NORMAL shift type WHEN invoke THEN delegates to repository and returns session id`() =
        runBlocking {
            // GIVEN
            coEvery { repository.startSession(ShiftType.NORMAL) } returns 1L

            // WHEN
            val result = useCase(ShiftType.NORMAL)

            // THEN
            assertEquals(1L, result)
            coVerify(exactly = 1) { repository.startSession(ShiftType.NORMAL) }
        }

    @Test
    fun `GIVEN EXTENDED shift type WHEN invoke THEN delegates with correct type`() = runBlocking {
        // GIVEN
        coEvery { repository.startSession(ShiftType.EXTENDED) } returns 2L

        // WHEN
        val result = useCase(ShiftType.EXTENDED)

        // THEN
        assertEquals(2L, result)
        coVerify(exactly = 1) { repository.startSession(ShiftType.EXTENDED) }
    }

    @Test
    fun `GIVEN NIGHT shift type WHEN invoke THEN delegates with correct type`() = runBlocking {
        // GIVEN
        coEvery { repository.startSession(ShiftType.NIGHT) } returns 3L

        // WHEN
        val result = useCase(ShiftType.NIGHT)

        // THEN
        assertEquals(3L, result)
        coVerify(exactly = 1) { repository.startSession(ShiftType.NIGHT) }
    }

    @Test
    fun `GIVEN REST shift type WHEN invoke THEN delegates with correct type`() = runBlocking {
        // GIVEN
        coEvery { repository.startSession(ShiftType.REST) } returns 4L

        // WHEN
        val result = useCase(ShiftType.REST)

        // THEN
        assertEquals(4L, result)
        coVerify(exactly = 1) { repository.startSession(ShiftType.REST) }
    }

    @Test
    fun `GIVEN repository returns large id WHEN invoke THEN id is propagated without truncation`() =
        runBlocking {
            // GIVEN
            val largeId = Long.MAX_VALUE
            coEvery { repository.startSession(ShiftType.SPLIT) } returns largeId

            // WHEN
            val result = useCase(ShiftType.SPLIT)

            // THEN
            assertEquals(largeId, result)
        }

    @Test
    fun `GIVEN invoke called WHEN finished THEN repository called exactly once (no extra calls)`() =
        runBlocking {
            // GIVEN
            coEvery { repository.startSession(any()) } returns 99L

            // WHEN
            useCase(ShiftType.NORMAL)

            // THEN
            coVerify(exactly = 1) { repository.startSession(any()) }
        }
}
