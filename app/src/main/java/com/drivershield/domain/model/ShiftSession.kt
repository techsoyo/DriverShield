package com.drivershield.domain.model

import java.time.Instant

data class ShiftSession(
    val id: Long = 0,
    val type: ShiftType = ShiftType.NORMAL,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val isoWeek: Int = 0,
    val isoYear: Int = 0,
    val notes: String = ""
) {
    val isActive: Boolean get() = endTime == null
    val durationMillis: Long? get() = endTime?.let { it.toEpochMilli() - startTime.toEpochMilli() }
}
