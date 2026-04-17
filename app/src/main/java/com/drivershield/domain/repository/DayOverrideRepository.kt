package com.drivershield.domain.repository

import com.drivershield.domain.model.DayOverride
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Contrato de acceso a los overrides manuales del calendario de libranzas.
 *
 * Un [DayOverride] tiene prioridad sobre la configuración fija de [WorkSchedule.offDays]:
 * permite convertir un día laborable en libranza y viceversa para una fecha concreta.
 * [ToggleDayOverrideUseCase] elimina el override cuando el nuevo estado coincide con
 * el estado fijo, evitando filas redundantes en la tabla `day_overrides`.
 */
interface DayOverrideRepository {

    /**
     * Lectura puntual del override de una fecha concreta.
     *
     * @param date Fecha a consultar.
     * @return [DayOverride] si existe un override para esa fecha, `null` en caso contrario.
     */
    suspend fun getOverride(date: LocalDate): DayOverride?

    /**
     * Emite como stream reactivo todos los overrides dentro del rango [start, end] (ambos inclusive).
     * Usado por [GenerateReportUseCase] y [CalendarViewModel].
     */
    fun getOverridesInRange(start: LocalDate, end: LocalDate): Flow<List<DayOverride>>

    /**
     * Inserta o actualiza el override de una fecha.
     *
     * @param date       Fecha a sobrescribir.
     * @param isLibranza `true` para marcar como libranza, `false` para marcar como laborable.
     */
    suspend fun upsertOverride(date: LocalDate, isLibranza: Boolean)

    /**
     * Elimina el override de una fecha, restaurando el comportamiento definido por
     * [WorkSchedule.offDays] para ese día.
     *
     * @param date Fecha cuyo override se elimina.
     */
    suspend fun deleteOverride(date: LocalDate)
}
