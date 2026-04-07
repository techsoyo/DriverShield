package com.drivershield.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_events",
    foreignKeys = [
        ForeignKey(
            entity = ShiftSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class ShiftEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sessionId")
    val sessionId: Long,

    @ColumnInfo(name = "eventType")
    val eventType: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "elapsedRealtime", defaultValue = "0")
    val elapsedRealtime: Long = 0L,

    @ColumnInfo(name = "isSystemTimeReliable", defaultValue = "1")
    val isSystemTimeReliable: Boolean = true
)
