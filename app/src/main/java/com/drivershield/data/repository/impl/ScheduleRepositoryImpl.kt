package com.drivershield.data.repository.impl

import com.drivershield.data.local.db.dao.WorkScheduleDao
import com.drivershield.data.local.db.entity.WorkScheduleEntity
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val workScheduleDao: WorkScheduleDao
) : ScheduleRepository {

    override suspend fun saveSchedule(schedule: WorkSchedule) {
        val entity = WorkScheduleEntity(
            id = 1,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            offDays = schedule.offDays.joinToString(","),
            weeklyTargetMs = schedule.weeklyTargetMs,
            dailyTargetMs = schedule.dailyTargetMs,
            cycleStartEpoch = schedule.cycleStartEpoch
        )
        workScheduleDao.save(entity)
    }

    override fun getScheduleFlow(): Flow<WorkSchedule?> =
        workScheduleDao.getScheduleFlow().map { it?.toDomain() }

    override suspend fun getSchedule(): WorkSchedule? =
        workScheduleDao.getSchedule()?.toDomain()

    private fun WorkScheduleEntity.toDomain() = WorkSchedule(
        startTime = startTime,
        endTime = endTime,
        offDays = offDays.split(",").mapNotNull { it.toIntOrNull() },
        weeklyTargetMs = weeklyTargetMs,
        dailyTargetMs = dailyTargetMs,
        cycleStartEpoch = cycleStartEpoch
    )

}
