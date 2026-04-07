package com.drivershield.domain.repository

import com.drivershield.domain.model.WorkSchedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    suspend fun saveSchedule(schedule: WorkSchedule)
    fun getScheduleFlow(): Flow<WorkSchedule?>
    suspend fun getSchedule(): WorkSchedule?
}
