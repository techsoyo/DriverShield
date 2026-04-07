package com.drivershield.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_schedule")
data class WorkScheduleEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "start_time")
    val startTime: String,

    @ColumnInfo(name = "end_time")
    val endTime: String,

    @ColumnInfo(name = "off_days")
    val offDays: String,

    @ColumnInfo(name = "weekly_target_ms")
    val weeklyTargetMs: Long,

    @ColumnInfo(name = "daily_target_ms")
    val dailyTargetMs: Long,

    @ColumnInfo(name = "cycle_start_epoch")
    val cycleStartEpoch: Long = 0L
)
