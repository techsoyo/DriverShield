package com.drivershield.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.drivershield.data.local.db.entity.ShiftSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO principal para la tabla `shift_sessions`.
 *
 * ## Estrategia Event Sourcing ligero
 * DriverShield no almacena estado mutado directamente en filas OLTP clásicas; en cambio
 * registra dos capas complementarias:
 *
 * 1. **Sesión** (`shift_sessions`): cabecera inmutable con `startTimestamp` (UTC epoch),
 *    `isoYear`/`isoWeekNumber` para búsquedas rápidas por semana, y `isTampered`
 *    como flag de integridad. `endTimestamp` y `durationMs` se completan **solo al
 *    cerrar el turno** mediante la transacción atómica [endSession].
 *
 * 2. **Eventos** (`shift_events` gestionados por [ShiftEventDao]): log inmutable de cada
 *    transición de estado (inicio, pausa, reanudación, fin). Permiten reconstruir la
 *    línea de tiempo completa de un turno para auditorías sin depender del estado
 *    calculado de la sesión.
 *
 * ## Rationale legal de `isTampered`
 * El flag `isTampered = true` indica que se detectó un cambio de reloj del sistema
 * ([Intent.ACTION_TIME_CHANGED]) durante la sesión. En una auditoría laboral o
 * inspección de Tráfico, los registros marcados como adulterados permiten distinguir
 * errores del dispositivo de posibles manipulaciones intencionadas de la jornada.
 * **Este campo nunca debe actualizarse de `true` a `false` por código de producción.**
 *
 * ## Índices
 * - `(isoYear, isoWeekNumber)`: optimiza las consultas de semana; acceso O(log n)
 *   aunque la tabla tenga años de registros.
 * - `(startTimestamp)`: permite filtros por rango de fechas sin full-scan.
 */
@Dao
interface ShiftDao {

    @Insert
    suspend fun insert(session: ShiftSessionEntity): Long

    @Update
    suspend fun update(session: ShiftSessionEntity)

    @Query("SELECT * FROM shift_sessions WHERE endTimestamp IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<ShiftSessionEntity?>

    @Query("""
        SELECT * FROM shift_sessions 
        WHERE isoYear = :isoYear AND isoWeekNumber = :isoWeekNumber 
        OR (endTimestamp IS NOT NULL AND endTimestamp >= :weekStartMillis AND startTimestamp < :weekEndMillis)
        ORDER BY startTimestamp ASC
    """)
    fun getSessionsByWeekWithOverflow(isoYear: Int, isoWeekNumber: Int, weekStartMillis: Long, weekEndMillis: Long): Flow<List<ShiftSessionEntity>>

    @Query("SELECT * FROM shift_sessions WHERE isoYear = :isoYear AND isoWeekNumber = :isoWeekNumber ORDER BY startTimestamp ASC")
    fun getSessionsByWeek(isoYear: Int, isoWeekNumber: Int): Flow<List<ShiftSessionEntity>>

    @Query("""
        SELECT SUM(durationMs) FROM shift_sessions
        WHERE isoYear = :isoYear AND isoWeekNumber = :isoWeekNumber AND type = 'WORK'
    """)
    fun getWeeklyWorkMs(isoYear: Int, isoWeekNumber: Int): Flow<Long?>

    @Query("""
        SELECT SUM(durationMs) FROM shift_sessions
        WHERE isoYear = :isoYear AND isoWeekNumber = :isoWeekNumber AND type = 'WORK'
    """)
    suspend fun getWeeklyWorkMsSync(isoYear: Int, isoWeekNumber: Int): Long?

    /**
     * Cierra atómicamente una sesión activa.
     *
     * Usa [@Transaction] para garantizar que la lectura de [getById] y la escritura de
     * [update] son atómicas. Si la sesión ya no existe (borrado concurrente), la operación
     * es un no-op seguro.
     *
     * @param id         Identificador de la sesión a cerrar.
     * @param endTs      Epoch UTC del momento de cierre ([System.currentTimeMillis]).
     * @param durationMs Duración total calculada por [TimerService].
     * @param isTampered `true` si [TimerService] detectó una manipulación de reloj.
     */
    @Transaction
    suspend fun endSession(id: Long, endTs: Long, durationMs: Long, isTampered: Boolean) {
        val session = getById(id) ?: return
        update(session.copy(endTimestamp = endTs, durationMs = durationMs, isTampered = isTampered))
    }

    @Query("SELECT * FROM shift_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShiftSessionEntity?

    @Query("SELECT * FROM shift_sessions ORDER BY startTimestamp DESC")
    fun getAllSessionsFlow(): Flow<List<ShiftSessionEntity>>
}
