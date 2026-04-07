package com.drivershield.domain.model

data class WorkSchedule(
    val startTime: String,
    val endTime: String,
    val offDays: List<Int>,
    val weeklyTargetMs: Long,
    val dailyTargetMs: Long,
    val cycleStartEpoch: Long = 0L
) {
    fun isOffDay(dayOfWeek: Int): Boolean = offDays.contains(dayOfWeek)
    fun hasCycle(): Boolean = cycleStartEpoch > 0L
}
