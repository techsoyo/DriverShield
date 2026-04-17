package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Cierra una sesión de turno activa y devuelve la sesión con todos sus campos completos.
 *
 * Internamente ejecuta la transacción atómica [ShiftDao.endSession]:
 * rellena `endTimestamp`, calcula `durationMs` y propaga el flag `isTampered`
 * si [TimerService] detectó una manipulación del reloj durante la sesión.
 *
 * El flag `isTampered` nunca se revierte a `false` una vez establecido:
 * queda como evidencia permanente en `shift_sessions` para auditorías.
 */
class EndShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    /**
     * @param sessionId ID de la sesión activa a cerrar.
     * @return [ShiftSession] con `endTimestamp` y `durationMs` rellenos.
     * @throws IllegalStateException si la sesión no existe o ya está cerrada.
     */
    suspend operator fun invoke(sessionId: Long): ShiftSession =
        repository.endSession(sessionId)
}
