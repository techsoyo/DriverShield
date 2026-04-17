package com.drivershield.domain.repository

import com.drivershield.domain.model.WorkSchedule
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acceso a la configuración de horario del conductor.
 *
 * La tabla `work_schedule` contiene exactamente una fila (la configuración activa).
 * `offDays` se serializa como JSON (p. ej. `[6,7]` para sábado y domingo).
 */
interface ScheduleRepository {

    /**
     * Persiste la configuración de horario, sobreescribiendo cualquier valor previo.
     *
     * @param schedule Nuevo horario a guardar.
     */
    suspend fun saveSchedule(schedule: WorkSchedule)

    /**
     * Emite la configuración activa como stream reactivo.
     * Emite `null` si el usuario no ha configurado su horario todavía.
     */
    fun getScheduleFlow(): Flow<WorkSchedule?>

    /**
     * Lectura puntual de la configuración activa.
     *
     * @return [WorkSchedule] configurado, o `null` si no hay ninguno.
     */
    suspend fun getSchedule(): WorkSchedule?
}
