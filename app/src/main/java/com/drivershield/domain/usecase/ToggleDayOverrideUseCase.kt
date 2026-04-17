package com.drivershield.domain.usecase

import com.drivershield.domain.repository.DayOverrideRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Invierte el estado de libranza de un día natural concreto.
 *
 * Regla de optimización de BD:
 * Si el nuevo estado coincide con el estado fijo de la configuración, el override
 * se elimina (en lugar de persistir un registro redundante).
 *
 * @param date           Día sobre el que se aplica el toggle.
 * @param fixedOffDays   Lista de días de la semana (ISO 1=Lun…7=Dom) marcados
 *                       como libranza fija en la configuración del usuario.
 */
class ToggleDayOverrideUseCase @Inject constructor(
    private val repository: DayOverrideRepository
) {
    suspend operator fun invoke(date: LocalDate, fixedOffDays: List<Int>) {
        val dayOfWeek = date.dayOfWeek.value          // 1=Lun … 7=Dom (ISO)
        val isFixedOff = fixedOffDays.contains(dayOfWeek)
        val existing = repository.getOverride(date)
        val currentIsLibranza = existing?.isLibranza ?: isFixedOff
        val newIsLibranza = !currentIsLibranza

        if (newIsLibranza == isFixedOff) {
            // El nuevo estado es idéntico al fijo → sin override necesario → limpia BD
            repository.deleteOverride(date)
        } else {
            repository.upsertOverride(date, newIsLibranza)
        }
    }
}
