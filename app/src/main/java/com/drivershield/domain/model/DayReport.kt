package com.drivershield.domain.model

import java.time.LocalDate

data class DayReport(
    val date: LocalDate,
    val sessions: List<SessionReport>
) {
    val totalWorkMs: Long
        get() = sessions.sumOf { it.totalWorkMs }

    val totalRestMs: Long
        get() = sessions.sumOf { it.totalRestMs }

    val hasWorkExcess: Boolean
        get() = sessions.any { it.hasWorkExcess }

    val hasRestExcess: Boolean
        get() = sessions.any { it.hasRestExcess }
}

data class SessionReport(
    val sessionId: Long,
    val events: List<TimelineEvent>,
    val totalWorkMs: Long,
    val totalRestMs: Long,
    val isTampered: Boolean = false
) {
    val hasWorkExcess: Boolean
        get() = totalWorkMs > 8L * 60 * 60 * 1000

    val hasRestExcess: Boolean
        get() = totalRestMs > 4L * 60 * 60 * 1000
}

data class TimelineEvent(
    val timestamp: Long,
    val type: EventType,
    val description: String
)
