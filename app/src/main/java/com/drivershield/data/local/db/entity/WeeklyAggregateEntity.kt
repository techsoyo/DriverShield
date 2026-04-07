package com.drivershield.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "weekly_aggregates",
    primaryKeys = ["isoYear", "isoWeekNumber"]
)
data class WeeklyAggregateEntity(
    val isoYear: Int,
    val isoWeekNumber: Int,
    val totalWorkMs: Long,
    val totalRestMs: Long,
    val sessionCount: Int,
    val lastUpdated: Long
)
