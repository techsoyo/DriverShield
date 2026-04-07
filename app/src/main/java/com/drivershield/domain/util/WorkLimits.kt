package com.drivershield.domain.util

object WorkLimits {
    const val MAX_WORK_DAY_MS = 8L * 60 * 60 * 1000
    const val MAX_REST_SHIFT_MS = 4L * 60 * 60 * 1000

    fun hasWorkDayExcess(workMs: Long): Boolean = workMs > MAX_WORK_DAY_MS
    fun hasRestShiftExcess(restMs: Long): Boolean = restMs > MAX_REST_SHIFT_MS
}
