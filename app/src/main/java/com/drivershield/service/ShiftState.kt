package com.drivershield.service

enum class ShiftState {
    DETENIDO,
    TRABAJANDO,
    EN_PAUSA
}

data class TimerState(
    val state: ShiftState = ShiftState.DETENIDO,
    val workProgressMs: Long = 0L,
    val workRemainingMs: Long = 0L,
    val restProgressMs: Long = 0L,
    val restRemainingMs: Long = 0L,
    val weeklyProgressMs: Long = 0L,
    val sessionId: Long = 0L,
    val startEpoch: Long = 0L,
    val pauseEpoch: Long = 0L,
    val accumulatedWorkBeforePause: Long = 0L,
    val accumulatedRestBeforePause: Long = 0L,
    val baseWeeklyMs: Long = 0L
) {
    companion object {
        const val MAX_WORK_MS = 8L * 60 * 60 * 1000
        const val MIN_REST_MS = 4L * 60 * 60 * 1000
        const val MAX_WEEKLY_MS = 40L * 60 * 60 * 1000
    }
}
