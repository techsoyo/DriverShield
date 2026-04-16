package com.drivershield.data.repository.impl

import android.os.SystemClock
import androidx.room.withTransaction
import com.drivershield.data.local.db.AppDatabase
import com.drivershield.data.local.db.dao.ShiftDao
import com.drivershield.data.local.db.dao.ShiftEventDao
import com.drivershield.data.local.db.entity.ShiftEventEntity
import com.drivershield.data.local.db.entity.ShiftSessionEntity
import com.drivershield.data.local.db.entity.WeeklyAggregateEntity
import com.drivershield.domain.model.*
import com.drivershield.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import javax.inject.Inject

class ShiftRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val shiftDao: ShiftDao,
    private val shiftEventDao: ShiftEventDao,
    private val sessionDataStore: com.drivershield.data.local.datastore.SessionDataStore
) : ShiftRepository {

    override suspend fun startSession(type: ShiftType): Long {
        val now = System.currentTimeMillis()
        val realTime = SystemClock.elapsedRealtime()
        val weekFields = WeekFields.ISO
        val today = LocalDate.now()

        val entity = ShiftSessionEntity(
            startTimestamp = now,
            endTimestamp = null,
            type = type.name,
            durationMs = null,
            isoWeekNumber = today.get(weekFields.weekOfWeekBasedYear()),
            isoYear = today.get(weekFields.weekBasedYear()),
            notes = ""
        )
        val id = shiftDao.insert(entity)

        shiftEventDao.insert(
            ShiftEventEntity(
                sessionId = id,
                eventType = EventType.START_WORK.name,
                timestamp = now,
                elapsedRealtime = realTime,
                isSystemTimeReliable = true
            )
        )
        return id
    }

    override suspend fun endSession(id: Long): ShiftSession {
        return database.withTransaction {
            val session = shiftDao.getById(id) ?: throw IllegalStateException("Session $id not found")
            val endTs = System.currentTimeMillis()
            val realTime = SystemClock.elapsedRealtime()
            val durationMs = endTs - session.startTimestamp

            val isDataStoreTampered = sessionDataStore.isSessionTampered.first()
            val startEvent = shiftEventDao.getEventsForSessionSync(id).firstOrNull { it.eventType == EventType.START_WORK.name }
            var isReliable = true
            if (startEvent != null) {
                val expectedNow = startEvent.timestamp + (realTime - startEvent.elapsedRealtime)
                if (kotlin.math.abs(endTs - expectedNow) > 10_000L) isReliable = false
            }

            val finalTampered = !isReliable || isDataStoreTampered
            shiftDao.endSession(id, endTs, durationMs, finalTampered)

            shiftEventDao.insert(
                ShiftEventEntity(
                    sessionId = id,
                    eventType = EventType.END_SHIFT.name,
                    timestamp = endTs,
                    elapsedRealtime = realTime,
                    isSystemTimeReliable = isReliable
                )
            )

            val currentWorkMs = shiftDao.getWeeklyWorkMsSync(session.isoYear, session.isoWeekNumber) ?: 0L
            database.weeklyAggregateDao().upsert(
                WeeklyAggregateEntity(
                    isoYear = session.isoYear,
                    isoWeekNumber = session.isoWeekNumber,
                    totalWorkMs = currentWorkMs,
                    totalRestMs = 0L,
                    sessionCount = 1,
                    lastUpdated = endTs
                )
            )

            session.toDomain().copy(endTime = Instant.ofEpochMilli(endTs))
        }
    }

    override suspend fun recordEvent(sessionId: Long, type: EventType): Long {
        val now = System.currentTimeMillis()
        val realTime = SystemClock.elapsedRealtime()
        val startEvent = shiftEventDao.getEventsForSessionSync(sessionId).firstOrNull { it.eventType == EventType.START_WORK.name }
        var isReliable = true
        if (startEvent != null) {
            val expectedNow = startEvent.timestamp + (realTime - startEvent.elapsedRealtime)
            if (kotlin.math.abs(now - expectedNow) > 10_000L) isReliable = false
        }

        return shiftEventDao.insert(
            ShiftEventEntity(
                sessionId = sessionId,
                eventType = type.name,
                timestamp = now,
                elapsedRealtime = realTime,
                isSystemTimeReliable = isReliable
            )
        )
    }

    override fun getActiveSession(): Flow<ShiftSession?> =
        shiftDao.getActiveSessionFlow().map { it?.toDomain() }

    override fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>> =
        shiftDao.getSessionsByWeek(isoYear, week).map { list -> list.map { it.toDomain() } }

    override fun getSessionsByWeekWithOverflow(isoYear: Int, week: Int, weekStartMillis: Long, weekEndMillis: Long): Flow<List<ShiftSession>> =
        shiftDao.getSessionsByWeekWithOverflow(isoYear, week, weekStartMillis, weekEndMillis).map { list -> list.map { it.toDomain() } }

    override suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long =
        shiftDao.getWeeklyWorkMs(isoYear, week).first() ?: 0L

    override fun getEventsForSession(sessionId: Long): Flow<List<ShiftEvent>> =
        shiftEventDao.getEventsForSession(sessionId).map { list ->
            list.map { ShiftEvent(it.id, it.sessionId, EventType.valueOf(it.eventType), it.timestamp) }
        }

    override fun getAllSessionsWithEvents(): Flow<List<DayReport>> =
        shiftDao.getAllSessionsFlow().map { sessions ->
            sessions
                .groupBy { Instant.ofEpochMilli(it.startTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
                .map { (date, daySessions) ->
                    DayReport(
                        date = date,
                        sessions = daySessions.map { entity ->
                            val events = buildList {
                                add(
                                    TimelineEvent(
                                        timestamp = entity.startTimestamp,
                                        type = EventType.START_WORK,
                                        description = "Inicio de turno"
                                    )
                                )
                                if (entity.endTimestamp != null) {
                                    add(
                                        TimelineEvent(
                                            timestamp = entity.endTimestamp,
                                            type = EventType.END_SHIFT,
                                            description = "Fin de turno"
                                        )
                                    )
                                }
                            }
                            SessionReport(
                                sessionId = entity.id,
                                startTimestamp = entity.startTimestamp,
                                endTimestamp = entity.endTimestamp,
                                events = events,
                                totalWorkMs = entity.durationMs ?: 0L,
                                totalRestMs = 0L,
                                isTampered = entity.isTampered
                            )
                        }
                    )
                }.sortedByDescending { it.date }
        }

    override suspend fun getShiftById(id: Long): ShiftSession? =
        shiftDao.getById(id)?.toDomain()

    override suspend fun deleteShiftById(id: Long) {
        shiftDao.deleteById(id)
    }

    override suspend fun updateShift(session: ShiftSession) {
        shiftDao.updateSession(session.toEntity())
    }

    override suspend fun reopenShift(id: Long) {
        shiftDao.reopenSession(id)
    }

    override suspend fun createManualShift(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        type: ShiftType
    ): Long {
        val zone = ZoneId.systemDefault()
        val weekFields = WeekFields.ISO
        val startTs = date.atTime(startHour, startMinute).atZone(zone).toInstant().toEpochMilli()
        // Si la hora de fin es menor que la de inicio → el segmento cruza medianoche → fin es del día siguiente
        val endMinutesOfDay = endHour * 60 + endMinute
        val startMinutesOfDay = startHour * 60 + startMinute
        val endDate = if (endMinutesOfDay <= startMinutesOfDay) date.plusDays(1) else date
        val endTs = endDate.atTime(endHour, endMinute).atZone(zone).toInstant().toEpochMilli()

        val entity = ShiftSessionEntity(
            startTimestamp = startTs,
            endTimestamp = endTs,
            type = type.name,
            durationMs = endTs - startTs,
            isoWeekNumber = date.get(weekFields.weekOfWeekBasedYear()),
            isoYear = date.get(weekFields.weekBasedYear()),
            notes = ""
        )
        val id = shiftDao.insert(entity)
        shiftEventDao.insert(ShiftEventEntity(sessionId = id, eventType = EventType.START_WORK.name, timestamp = startTs, elapsedRealtime = 0L, isSystemTimeReliable = true))
        shiftEventDao.insert(ShiftEventEntity(sessionId = id, eventType = EventType.END_SHIFT.name, timestamp = endTs, elapsedRealtime = 0L, isSystemTimeReliable = true))
        return id
    }

    // --- MAPEADORES CORREGIDOS ---

    private fun ShiftSessionEntity.toDomain() = ShiftSession(
        id = id,
        type = ShiftType.valueOf(type),
        startTime = Instant.ofEpochMilli(startTimestamp),
        endTime = endTimestamp?.let { Instant.ofEpochMilli(it) },
        isoWeek = isoWeekNumber,
        isoYear = isoYear,
        notes = notes ?: ""
    )

    private fun ShiftSession.toEntity() = ShiftSessionEntity(
        id = id,
        startTimestamp = startTime.toEpochMilli(),
        endTimestamp = endTime?.toEpochMilli(),
        type = type.name,
        durationMs = endTime?.let { it.toEpochMilli() - startTime.toEpochMilli() },
        isoWeekNumber = isoWeek, // Corregido: isoWeek (domain) -> isoWeekNumber (entity)
        isoYear = isoYear,       // Corregido: isoYear (domain) -> isoYear (entity)
        notes = notes
    )
}
