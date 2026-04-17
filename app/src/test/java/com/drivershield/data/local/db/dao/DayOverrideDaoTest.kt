package com.drivershield.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivershield.data.local.db.AppDatabase
import com.drivershield.data.local.db.entity.DayOverrideEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Tests de integración para DayOverrideDao usando Room in-memory + Robolectric.
 *
 * La base de datos se crea de cero en cada test (in-memory) por lo que
 * no se requieren migraciones; Room genera el esquema completo v7 desde
 * las anotaciones @Entity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DayOverrideDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DayOverrideDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dayOverrideDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ─── Test 9 ────────────────────────────────────────────────────────────

    @Test
    fun `GIVEN base de datos vacia WHEN getOverride THEN devuelve null`() = runBlocking {
        // GIVEN (BD vacía por setUp())
        val epochDay = LocalDate.parse("2026-04-16").toEpochDay()

        // WHEN
        val result = dao.getOverride(epochDay)

        // THEN
        assertNull("Una BD vacía no debe tener overrides", result)
    }

    // ─── Test 10 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override insertado WHEN getOverride THEN devuelve la entidad correcta`() = runBlocking {
        // GIVEN
        val date = LocalDate.parse("2026-04-16")
        val entity = DayOverrideEntity(
            dateEpochDay = date.toEpochDay(),
            isLibranza = true,
            manualOverride = true
        )
        dao.insertOverride(entity)

        // WHEN
        val result = dao.getOverride(date.toEpochDay())

        // THEN
        assertEquals("El epochDay debe coincidir", date.toEpochDay(), result?.dateEpochDay)
        assertEquals("isLibranza debe ser true", true, result?.isLibranza)
        assertEquals("manualOverride debe ser true", true, result?.manualOverride)
    }

    // ─── Test 11 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override insertado WHEN deleteOverride THEN getOverride devuelve null`() = runBlocking {
        // GIVEN
        val epochDay = LocalDate.parse("2026-04-17").toEpochDay()
        dao.insertOverride(DayOverrideEntity(dateEpochDay = epochDay, isLibranza = false))

        // WHEN
        dao.deleteOverride(epochDay)

        // THEN
        val result = dao.getOverride(epochDay)
        assertNull("Tras el borrado, getOverride debe devolver null", result)
    }

    // ─── Test 12 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override insertado WHEN insert mismo PK con valor distinto THEN persiste ultimo valor (REPLACE)`() =
        runBlocking {
            // GIVEN
            val epochDay = LocalDate.parse("2026-04-18").toEpochDay()
            dao.insertOverride(DayOverrideEntity(dateEpochDay = epochDay, isLibranza = true))

            // WHEN: re-insertar con isLibranza=false (REPLACE)
            dao.insertOverride(DayOverrideEntity(dateEpochDay = epochDay, isLibranza = false))

            // THEN: debe persistir el último valor insertado
            val result = dao.getOverride(epochDay)
            assertEquals("OnConflictStrategy.REPLACE debe guardar el último valor", false, result?.isLibranza)
        }

    // ─── Test 13 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN varios overrides WHEN getOverridesInRange THEN filtra correctamente por rango`() =
        runBlocking {
            // GIVEN: días del 13 al 19 de Abril 2026
            val days = (13..19).map { LocalDate.parse("2026-04-%02d".format(it)) }
            days.forEach { date ->
                dao.insertOverride(DayOverrideEntity(dateEpochDay = date.toEpochDay(), isLibranza = true))
            }

            // WHEN: consultar solo el rango Lunes-Viernes (13–17 Abril)
            val monday = LocalDate.parse("2026-04-13")
            val friday = LocalDate.parse("2026-04-17")
            val result = dao.getOverridesInRange(monday.toEpochDay(), friday.toEpochDay()).first()

            // THEN: solo deben aparecer los 5 días de la semana laboral
            assertEquals("Solo deben devolver los overrides del rango (5 días)", 5, result.size)
            assertTrue(
                "No debe incluir el sábado",
                result.none { it.dateEpochDay == LocalDate.parse("2026-04-18").toEpochDay() }
            )
        }

    // ─── Test 14 ───────────────────────────────────────────────────────────

    @Test
    fun `GIVEN override insertado WHEN se vuelve a consultar en la misma sesion THEN datos persisten`() =
        runBlocking {
            // GIVEN: insertar y confirmar
            val epochDay = LocalDate.parse("2026-04-20").toEpochDay()
            dao.insertOverride(DayOverrideEntity(dateEpochDay = epochDay, isLibranza = true))
            val firstRead = dao.getOverride(epochDay)
            assertEquals(true, firstRead?.isLibranza)

            // WHEN: segunda consulta en la misma sesión ("reinicio simulado" = nueva query)
            val secondRead = dao.getOverride(epochDay)

            // THEN: los datos deben persistir dentro de la sesión de BD en memoria
            assertEquals("Los datos deben persistir tras segunda consulta", true, secondRead?.isLibranza)
            assertEquals("El epochDay debe mantenerse intacto", epochDay, secondRead?.dateEpochDay)
        }
}
