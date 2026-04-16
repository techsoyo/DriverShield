package com.drivershield.domain.repository

import com.drivershield.domain.model.ShiftEvent
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ShiftRepository {
    // --- Funciones de Operación Estándar ---
    suspend fun startSession(type: ShiftType): Long
    suspend fun endSession(id: Long): ShiftSession
    suspend fun recordEvent(sessionId: Long, type: com.drivershield.domain.model.EventType): Long
    fun getActiveSession(): Flow<ShiftSession?>
    fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>>
    fun getSessionsByWeekWithOverflow(isoYear: Int, week: Int, weekStartMillis: Long, weekEndMillis: Long): Flow<List<ShiftSession>>
    suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long
    fun getEventsForSession(sessionId: Long): Flow<List<ShiftEvent>>
    fun getAllSessionsWithEvents(): Flow<List<com.drivershield.domain.model.DayReport>>

    // --- Funciones para Gestión y Edición de Horas (NUEVAS) ---

    /**
     * Obtiene una sesión específica por su ID.
     */
    suspend fun getShiftById(id: Long): ShiftSession?

    /**
     * Elimina una sesión (ej. sesiones accidentales como la 4 y 5).
     */
    suspend fun deleteShiftById(id: Long)

    /**
     * Actualiza los datos de una sesión (ej. después de editar horas).
     */
    suspend fun updateShift(session: ShiftSession)

    /**
     * Reabre una sesión que fue cerrada por error (ej. la sesión 3).
     */
    suspend fun reopenShift(id: Long)

    /**
     * Crea un turno completo con fecha y horas manuales para días sin registro.
     */
    suspend fun createManualShift(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        type: ShiftType
    ): Long
}