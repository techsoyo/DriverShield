package com.drivershield.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivershield.data.local.db.entity.WeeklyAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: WeeklyAggregateEntity)

    @Query("SELECT * FROM weekly_aggregates WHERE isoYear = :isoYear AND isoWeekNumber = :isoWeekNumber LIMIT 1")
    fun getByWeek(isoYear: Int, isoWeekNumber: Int): Flow<WeeklyAggregateEntity?>
}
