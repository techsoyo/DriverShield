package com.drivershield.data.repository.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivershield.data.local.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Tests de integración para [DayOverrideRepositoryImpl].
 *
 * Validan la persistencia de los DayOverrides a través de la capa de repositorio
 * (no directamente sobre el DAO), usando Room in-memory + Robolectric.
 *
 * GIVEN/WHEN/THEN siguiendo las convenciones del proyecto (AGENTS.md).
 *
 * Los IDs de test siguen la numeración correlativa de la suite:
 * (DAO: 9-11) → Repositorio: 12-17
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DayOverrideRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DayOverrideRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DayOverrideRepositoryImpl(database.dayOverrideDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ─── Test 12 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN BD vacia WHEN getOverride THEN devuelve null`() = runBlocking {
        // GIVEN (BD vacía por setUp)
        val date = LocalDate.parse("2026-04-16")

        // WHEN
        val result = repository.getOverride(date)

        // THEN
        assertNull("Sin datos el repositorio debe devolver null", result)
    }

    // ─── Test 13 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN upsertOverride isLibranza true WHEN getOverride THEN devuelve DayOverride correcto`() =
        runBlocking {
            // GIVEN
            val date = LocalDate.parse("2026-04-16")

            // WHEN
            repository.upsertOverride(date, isLibranza = true)
            val result = repository.getOverride(date)

            // THEN
            assertNotNull("Debe existir el override tras upsert", result)
            assertEquals("La fecha debe coincidir", date, result?.date)
            assertTrue("isLibranza debe ser true", result?.isLibranza == true)
        }

    // ─── Test 14 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override existente WHEN upsertOverride con valor opuesto THEN persiste el nuevo valor`() =
        runBlocking {
            // GIVEN: override con isLibranza = true
            val date = LocalDate.parse("2026-04-17")
            repository.upsertOverride(date, isLibranza = true)

            // WHEN: upsert con isLibranza = false (conductor fuerza día laboral)
            repository.upsertOverride(date, isLibranza = false)
            val result = repository.getOverride(date)

            // THEN: el valor debe haberse reemplazado (ON CONFLICT REPLACE)
            assertNotNull(result)
            assertFalse("isLibranza debe haberse cambiado a false", result?.isLibranza == true)
        }

    // ─── Test 15 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override insertado WHEN deleteOverride THEN getOverride devuelve null`() =
        runBlocking {
            // GIVEN
            val date = LocalDate.parse("2026-04-18")
            repository.upsertOverride(date, isLibranza = true)

            // WHEN
            repository.deleteOverride(date)

            // THEN
            val result = repository.getOverride(date)
            assertNull("Tras borrar el override, getOverride debe ser null", result)
        }

    // ─── Test 16 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN overrides en rango WHEN getOverridesInRange THEN devuelve solo los del rango`() =
        runBlocking {
            // GIVEN: insertamos 3 fechas, pero consultamos solo un rango de 2
            val lunes    = LocalDate.parse("2026-04-14")   // dentro del rango
            val martes   = LocalDate.parse("2026-04-15")   // dentro del rango
            val domingo  = LocalDate.parse("2026-04-19")   // fuera del rango

            repository.upsertOverride(lunes,   isLibranza = true)
            repository.upsertOverride(martes,  isLibranza = false)
            repository.upsertOverride(domingo, isLibranza = true)

            // WHEN: rango lunes → martes inclusive
            val result = repository.getOverridesInRange(lunes, martes).first()

            // THEN
            assertEquals("Solo deben aparecer 2 overrides en el rango", 2, result.size)
            val dates = result.map { it.date }
            assertTrue("Lunes debe estar en el rango", lunes in dates)
            assertTrue("Martes debe estar en el rango", martes in dates)
            assertFalse("Domingo NO debe estar en el rango", domingo in dates)
        }

    // ─── Test 17 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN BD vacia WHEN getOverridesInRange THEN devuelve lista vacia`() = runBlocking {
        // GIVEN (BD vacía)
        val start = LocalDate.parse("2026-04-13")
        val end   = LocalDate.parse("2026-04-19")

        // WHEN
        val result = repository.getOverridesInRange(start, end).first()

        // THEN
        assertTrue("Sin datos el flow debe emitir lista vacía", result.isEmpty())
    }
}
