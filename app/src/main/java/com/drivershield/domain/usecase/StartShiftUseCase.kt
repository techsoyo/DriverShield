package com.drivershield.domain.usecase

import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.repository.ShiftRepository
import javax.inject.Inject

class StartShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(type: ShiftType): Long =
        repository.startSession(type)
}
