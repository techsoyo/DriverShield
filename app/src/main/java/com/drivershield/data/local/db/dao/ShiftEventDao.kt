package com.drivershield.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivershield.data.local.db.entity.ShiftEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla `shift_events`: el log de eventos de cada turno.
 *
 * ## Rol en el Event Sourcing ligero
 * Cada cambio de estado del cronómetro (inicio, pausa, reanudación, fin) se registra
 * como una fila inmutable con `timestamp` (UTC epoch) y `elapsedRealtime`
 * ([android.os.SystemClock.elapsedRealtime]).
 *
 * El doble registro de tiempo tiene un propósito legal explícito:
 * - `timestamp`: tiempo de pared UTC, que puede ser manipulado.
 * - `elapsedRealtime`: tiempo monotónico desde el boot, imposible de manipular
 *   sin reiniciar el dispositivo. La divergencia entre ambos activa `isTampered`.
 *
 * Los eventos son **append-only**: nunca se actualizan ni borran individualmente.
 * La eliminación en cascada (`ForeignKey.CASCADE`) garantiza que al borrar una
 * sesión se borran todos sus eventos, manteniendo la integridad referencial.
 *
 * ## Reconstrucción de jornada
 * [ShiftRepositoryImpl.getAllSessionsWithEvents] combina [getAllEvents] con las sesiones
 * para reconstruir [DayReport] con bloques de trabajo/descanso preciosos para el
 * historial y los exportadores (PDF/CSV).
 */
@Dao
interface ShiftEventDao {

    @Insert
    suspend fun insert(event: ShiftEventEntity): Long

    @Query("SELECT * FROM shift_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsForSession(sessionId: Long): Flow<List<ShiftEventEntity>>

    @Query("SELECT * FROM shift_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getEventsForSessionSync(sessionId: Long): List<ShiftEventEntity>

    @Query("SELECT * FROM shift_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<ShiftEventEntity>
}
