package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.repository.ShiftRepository
import javax.inject.Inject

class EndShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(sessionId: Long): ShiftSession =
        repository.endSession(sessionId)
}
