package com.drivershield.domain.usecase

import com.drivershield.domain.model.DayOverride
import com.drivershield.domain.repository.DayOverrideRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ToggleDayOverrideUseCaseTest {

    private lateinit var repository: DayOverrideRepository
    private lateinit var useCase: ToggleDayOverrideUseCase

    /** Miércoles 15 de Abril 2026 (ISO dayOfWeek = 3, día laborable). */
    private val workDay = LocalDate.parse("2026-04-15")

    /** Sábado 18 de Abril 2026 (ISO dayOfWeek = 6, libranza fija típica). */
    private val fixedOffDay = LocalDate.parse("2026-04-18")

    /** Configuración fija: sábado (6) y domingo (7) libres. */
    private val fixedOffDays = listOf(6, 7)

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = ToggleDayOverrideUseCase(repository)
    }

    // ─── Test 5 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN dia fijo libre sin override WHEN toggle THEN inserta override isLibranza false (fuerza trabajo)`() =
        runBlocking {
            // GIVEN: sábado, libranza fija, sin override previo
            coEvery { repository.getOverride(fixedOffDay) } returns null

            // WHEN
            useCase(fixedOffDay, fixedOffDays)

            // THEN: nuevo estado=false (trabajo), ≠ fijo (libre) → upsert con isLibranza=false
            coVerify(exactly = 1) { repository.upsertOverride(fixedOffDay, false) }
            coVerify(exactly = 0) { repository.deleteOverride(any()) }
        }

    // ─── Test 6 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN dia laborable sin override WHEN toggle THEN inserta override isLibranza true (libre manual)`() =
        runBlocking {
            // GIVEN: miércoles, día laborable, sin override
            coEvery { repository.getOverride(workDay) } returns null

            // WHEN
            useCase(workDay, fixedOffDays)

            // THEN: nuevo estado=true (libre), ≠ fijo (trabajo) → upsert con isLibranza=true
            coVerify(exactly = 1) { repository.upsertOverride(workDay, true) }
            coVerify(exactly = 0) { repository.deleteOverride(any()) }
        }

    // ─── Test 7 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN dia laborable con override libre WHEN toggle THEN elimina override (vuelve a laborable)`() =
        runBlocking {
            // GIVEN: miércoles, laborable fijo, con override libre manual
            coEvery { repository.getOverride(workDay) } returns DayOverride(workDay, isLibranza = true)

            // WHEN
            useCase(workDay, fixedOffDays)

            // THEN: nuevo estado=false (trabajo) == fijo (trabajo) → eliminar override redundante
            coVerify(exactly = 1) { repository.deleteOverride(workDay) }
            coVerify(exactly = 0) { repository.upsertOverride(any(), any()) }
        }

    // ─── Test 8 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN dia fijo libre con override trabajo WHEN toggle THEN elimina override (vuelve a libranza fija)`() =
        runBlocking {
            // GIVEN: sábado, libranza fija, con override de trabajo forzado
            coEvery { repository.getOverride(fixedOffDay) } returns DayOverride(fixedOffDay, isLibranza = false)

            // WHEN
            useCase(fixedOffDay, fixedOffDays)

            // THEN: nuevo estado=true (libre) == fijo (libre) → eliminar override redundante
            coVerify(exactly = 1) { repository.deleteOverride(fixedOffDay) }
            coVerify(exactly = 0) { repository.upsertOverride(any(), any()) }
        }
}
