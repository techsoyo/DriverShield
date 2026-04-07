package com.drivershield.domain.model

enum class EventType {
    START_WORK,
    START_PAUSE,
    END_PAUSE,
    END_SHIFT
}

data class ShiftEvent(
    val id: Long = 0,
    val sessionId: Long,
    val type: EventType,
    val timestamp: Long
)
