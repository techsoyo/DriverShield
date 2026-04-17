package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Inicia una nueva sesión de turno y devuelve su ID.
 *
 * Delega en [ShiftRepository.startSession], que inserta la cabecera en
 * `shift_sessions` con `startTimestamp = System.currentTimeMillis()` y
 * deja `endTimestamp` a `null` (señal de sesión activa).
 *
 * Solo puede haber una sesión activa simultánea: el llamador (normalmente
 * [TimerService]) debe verificar [ShiftRepository.getActiveSession] antes
 * de invocar este use case.
 */
class StartShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    /**
     * @param type Tipo de turno a registrar (NORMAL, EXTENDED, NIGHT, SPLIT, REST).
     * @return ID generado por Room para la sesión recién creada.
     */
    suspend operator fun invoke(type: ShiftType): Long =
        repository.startSession(type)
}
