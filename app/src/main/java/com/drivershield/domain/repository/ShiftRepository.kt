package com.drivershield.domain.repository

import com.drivershield.domain.model.ShiftEvent
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    suspend fun startSession(type: ShiftType): Long
    suspend fun endSession(id: Long): ShiftSession
    suspend fun recordEvent(sessionId: Long, type: com.drivershield.domain.model.EventType): Long
    fun getActiveSession(): Flow<ShiftSession?>
    fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>>
    fun getSessionsByWeekWithOverflow(isoYear: Int, week: Int, weekStartMillis: Long, weekEndMillis: Long): Flow<List<ShiftSession>>
    suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long
    fun getEventsForSession(sessionId: Long): Flow<List<ShiftEvent>>
    fun getAllSessionsWithEvents(): Flow<List<com.drivershield.domain.model.DayReport>>
}
