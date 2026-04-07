package com.drivershield.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_sessions",
    indices = [
        Index(value = ["isoYear", "isoWeekNumber"]),
        Index(value = ["startTimestamp"])
    ]
)
data class ShiftSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "startTimestamp")
    val startTimestamp: Long,

    @ColumnInfo(name = "endTimestamp")
    val endTimestamp: Long?,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "durationMs")
    val durationMs: Long?,

    @ColumnInfo(name = "isoWeekNumber")
    val isoWeekNumber: Int,

    @ColumnInfo(name = "isoYear")
    val isoYear: Int,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "isTampered", defaultValue = "0")
    val isTampered: Boolean = false
)
