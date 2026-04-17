package com.drivershield.domain.repository

import com.drivershield.domain.model.ShiftEvent
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Contrato de acceso a los datos de sesiones de turno.
 *
 * Separa la capa de dominio de Room: los use cases dependen de esta interfaz,
 * no de [ShiftRepositoryImpl], lo que permite sustituir la implementación en tests.
 *
 * Convención de nomenclatura:
 * - Funciones `suspend` → lectura/escritura puntuales (un valor y listo).
 * - Funciones que retornan `Flow` → streams reactivos que emiten cada vez que
 *   Room detecta un cambio en la tabla subyacente.
 */
interface ShiftRepository {

    /**
     * Inserta una nueva sesión con `startTimestamp = now()` y `endTimestamp = null`.
     *
     * @param type Tipo de turno a iniciar.
     * @return ID generado por Room para la sesión creada.
     */
    suspend fun startSession(type: ShiftType): Long

    /**
     * Cierra atómicamente la sesión indicada rellenando `endTimestamp` y `durationMs`.
     *
     * @param id ID de la sesión activa.
     * @return [ShiftSession] con todos los campos completados.
     * @throws IllegalStateException si la sesión no existe o ya está cerrada.
     */
    suspend fun endSession(id: Long): ShiftSession

    /**
     * Añade una entrada al log inmutable de eventos de la sesión.
     *
     * @param sessionId ID de la sesión a la que pertenece el evento.
     * @param type      Tipo de transición (START_WORK, START_PAUSE, END_PAUSE, END_SHIFT).
     * @return ID generado por Room para el evento insertado.
     */
    suspend fun recordEvent(sessionId: Long, type: com.drivershield.domain.model.EventType): Long

    /**
     * Emite la sesión activa (con `endTimestamp = null`) o `null` si no hay ninguna.
     * Se actualiza automáticamente cuando Room detecta cambios en `shift_sessions`.
     */
    fun getActiveSession(): Flow<ShiftSession?>

    /**
     * Emite las sesiones cuyo `isoYear`/`isoWeekNumber` coincide con la semana indicada.
     *
     * No incluye turnos nocturnos que empezaron antes del lunes de la semana.
     * Para esos casos usar [getSessionsByWeekWithOverflow].
     */
    fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>>

    /**
     * Como [getSessionsByWeek] pero incluye también sesiones que finalizaron dentro
     * de la ventana [weekStartMillis, weekEndMillis] aunque empezaran la semana anterior.
     * Necesario para contabilizar turnos nocturnos de domingo a lunes.
     */
    fun getSessionsByWeekWithOverflow(
        isoYear: Int,
        week: Int,
        weekStartMillis: Long,
        weekEndMillis: Long
    ): Flow<List<ShiftSession>>

    /**
     * Suma de `durationMs` de todas las sesiones cerradas de la semana ISO indicada.
     *
     * @return Total en milisegundos, o `0` si no hay sesiones cerradas.
     */
    suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long

    /**
     * Emite el log de eventos de una sesión, ordenado por timestamp ascendente.
     */
    fun getEventsForSession(sessionId: Long): Flow<List<ShiftEvent>>

    /**
     * Emite todas las sesiones agrupadas por día natural, con sus eventos incluidos.
     * Usado por [GenerateReportUseCase] e [HistoryViewModel].
     */
    fun getAllSessionsWithEvents(): Flow<List<com.drivershield.domain.model.DayReport>>

    /**
     * @param id ID de la sesión a consultar.
     * @return [ShiftSession] o `null` si no existe.
     */
    suspend fun getShiftById(id: Long): ShiftSession?

    /**
     * Elimina permanentemente una sesión y sus eventos (CASCADE en BD).
     *
     * @param id ID de la sesión a eliminar.
     */
    suspend fun deleteShiftById(id: Long)

    /**
     * Sobreescribe los datos de una sesión existente (edición de historial).
     *
     * @param session [ShiftSession] con los valores actualizados. El `id` debe existir.
     */
    suspend fun updateShift(session: ShiftSession)

    /**
     * Pone `endTimestamp = null` y `durationMs = null` en la sesión indicada,
     * dejándola como activa sin cerrar.
     *
     * @param id ID de la sesión a reabrir.
     */
    suspend fun reopenShift(id: Long)

    /**
     * Crea una sesión completamente cerrada con hora de inicio y fin especificadas
     * manualmente, para cubrir días sin registro automático.
     *
     * @param date        Fecha del turno (hora local).
     * @param startHour   Hora de inicio (0–23).
     * @param startMinute Minuto de inicio (0–59).
     * @param endHour     Hora de fin (0–23).
     * @param endMinute   Minuto de fin (0–59).
     * @param type        Tipo de turno.
     * @return ID generado por Room para la sesión creada.
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