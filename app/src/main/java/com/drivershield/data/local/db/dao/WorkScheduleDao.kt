package com.drivershield.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivershield.data.local.db.entity.WorkScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(schedule: WorkScheduleEntity)

    @Query("SELECT * FROM work_schedule WHERE id = 1 LIMIT 1")
    fun getScheduleFlow(): Flow<WorkScheduleEntity?>

    @Query("SELECT * FROM work_schedule WHERE id = 1 LIMIT 1")
    suspend fun getSchedule(): WorkScheduleEntity?
}
