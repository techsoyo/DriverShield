package com.drivershield.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_overrides")
data class DayOverrideEntity(
    @PrimaryKey
    val dateEpochDay: Long,
    val isLibranza: Boolean,
    val manualOverride: Boolean = true
)