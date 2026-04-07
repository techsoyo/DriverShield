package com.drivershield.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivershield.data.local.db.entity.DayOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayOverrideDao {
    @Query("SELECT * FROM day_overrides WHERE dateEpochDay = :epochDay")
    suspend fun getOverride(epochDay: Long): DayOverrideEntity?

    @Query("SELECT * FROM day_overrides WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    fun getOverridesInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<DayOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: DayOverrideEntity)

    @Query("DELETE FROM day_overrides WHERE dateEpochDay = :epochDay")
    suspend fun deleteOverride(epochDay: Long)
}