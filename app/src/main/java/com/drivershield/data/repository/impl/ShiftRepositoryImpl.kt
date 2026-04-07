package com.drivershield.data.repository.impl

import com.drivershield.data.local.db.dao.ShiftDao
import com.drivershield.data.local.db.dao.ShiftEventDao
import com.drivershield.data.local.db.entity.ShiftEventEntity
import com.drivershield.data.local.db.entity.ShiftSessionEntity
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.EventType
import com.drivershield.domain.model.SessionReport
import com.drivershield.domain.model.ShiftEvent
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.model.TimelineEvent
import com.drivershield.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import javax.inject.Inject
import android.os.SystemClock
import androidx.room.withTransaction
import com.drivershield.data.local.db.AppDatabase
import com.drivershield.data.local.db.entity.WeeklyAggregateEntity

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
            isoYear = today.get(weekFields.weekBasedYear())
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
            val session = shiftDao.getById(id)
                ?: throw IllegalStateException("Session $id not found")
            val endTs = System.currentTimeMillis()
            val realTime = SystemClock.elapsedRealtime()
            val durationMs = endTs - session.startTimestamp
            
            val isDataStoreTampered = sessionDataStore.isSessionTampered.first()
            val startEvent = shiftEventDao.getEventsForSessionSync(id).firstOrNull { it.eventType == EventType.START_WORK.name }
            var isReliable = true
            if (startEvent != null) {
                val expectedNow = startEvent.timestamp + (realTime - startEvent.elapsedRealtime)
                if (kotlin.math.abs(endTs - expectedNow) > 10_000L) {
                    isReliable = false
                }
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
            val weeklyDao = database.weeklyAggregateDao()
            val aggregate = WeeklyAggregateEntity(
                isoYear = session.isoYear,
                isoWeekNumber = session.isoWeekNumber,
                totalWorkMs = currentWorkMs,
                totalRestMs = 0L,
                sessionCount = 1,
                lastUpdated = endTs
            )
            weeklyDao.upsert(aggregate)
            
            session.toDomain().copy(
                endTime = Instant.ofEpochMilli(endTs)
            )
        }
    }

    override suspend fun recordEvent(sessionId: Long, type: EventType): Long {
        val now = System.currentTimeMillis()
        val realTime = SystemClock.elapsedRealtime()
        
        val startEvent = shiftEventDao.getEventsForSessionSync(sessionId).firstOrNull { it.eventType == EventType.START_WORK.name }
        var isReliable = true
        if (startEvent != null) {
            val expectedNow = startEvent.timestamp + (realTime - startEvent.elapsedRealtime)
            if (kotlin.math.abs(now - expectedNow) > 10_000L) {
                isReliable = false
            }
        }
        
        val event = ShiftEventEntity(
            sessionId = sessionId,
            eventType = type.name,
            timestamp = now,
            elapsedRealtime = realTime,
            isSystemTimeReliable = isReliable
        )
        return shiftEventDao.insert(event)
    }

    override fun getActiveSession(): Flow<ShiftSession?> =
        shiftDao.getActiveSessionFlow().map { it?.toDomain() }

    override fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>> =
        shiftDao.getSessionsByWeek(isoYear, week).map { list -> list.map { it.toDomain() } }

    override fun getSessionsByWeekWithOverflow(
        isoYear: Int,
        week: Int,
        weekStartMillis: Long,
        weekEndMillis: Long
    ): Flow<List<ShiftSession>> =
        shiftDao.getSessionsByWeekWithOverflow(isoYear, week, weekStartMillis, weekEndMillis)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long =
        shiftDao.getWeeklyWorkMs(isoYear, week).first() ?: 0L

    override fun getEventsForSession(sessionId: Long): Flow<List<ShiftEvent>> =
        shiftEventDao.getEventsForSession(sessionId).map { list ->
            list.map {
                ShiftEvent(
                    id = it.id,
                    sessionId = it.sessionId,
                    type = EventType.valueOf(it.eventType),
                    timestamp = it.timestamp
                )
            }
        }

    override fun getAllSessionsWithEvents(): Flow<List<DayReport>> =
        shiftDao.getAllSessionsFlow().map { sessions ->
            val allEvents = shiftEventDao.getAllEvents()
            sessions
                .filter { it.endTimestamp != null }
                .groupBy { session ->
                    Instant.ofEpochMilli(session.startTimestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .map { (date, daySessions) ->
                    DayReport(
                        date = date,
                        sessions = daySessions.map { session ->
                            val sessionEvents = allEvents
                                .filter { it.sessionId == session.id }
                                .sortedBy { it.timestamp }

                            var workMs = 0L
                            var restMs = 0L
                            var lastWorkStart: Long? = null
                            var lastRestStart: Long? = null

                            val timelineEvents = sessionEvents.map { event ->
                                when (EventType.valueOf(event.eventType)) {
                                    EventType.START_WORK -> {
                                        lastWorkStart = event.timestamp
                                        TimelineEvent(
                                            timestamp = event.timestamp,
                                            type = EventType.START_WORK,
                                            description = "Inicio Trabajo"
                                        )
                                    }
                                    EventType.START_PAUSE -> {
                                        lastWorkStart?.let { start ->
                                            workMs += event.timestamp - start
                                        }
                                        lastRestStart = event.timestamp
                                        TimelineEvent(
                                            timestamp = event.timestamp,
                                            type = EventType.START_PAUSE,
                                            description = "Pausa (Descanso)"
                                        )
                                    }
                                    EventType.END_PAUSE -> {
                                        lastRestStart?.let { start ->
                                            restMs += event.timestamp - start
                                        }
                                        lastWorkStart = event.timestamp
                                        TimelineEvent(
                                            timestamp = event.timestamp,
                                            type = EventType.END_PAUSE,
                                            description = "Reanudar Trabajo"
                                        )
                                    }
                                    EventType.END_SHIFT -> {
                                        lastWorkStart?.let { start ->
                                            workMs += event.timestamp - start
                                        }
                                        TimelineEvent(
                                            timestamp = event.timestamp,
                                            type = EventType.END_SHIFT,
                                            description = "Fin de Turno"
                                        )
                                    }
                                }
                            }

                            SessionReport(
                                sessionId = session.id,
                                events = timelineEvents,
                                totalWorkMs = workMs,
                                totalRestMs = restMs,
                                isTampered = session.isTampered
                            )
                        }
                    )
                }
                .sortedByDescending { it.date }
        }

    private fun ShiftSessionEntity.toDomain() = ShiftSession(
        id = id,
        type = ShiftType.valueOf(type),
        startTime = Instant.ofEpochMilli(startTimestamp),
        endTime = endTimestamp?.let { Instant.ofEpochMilli(it) },
        notes = notes ?: ""
    )
}
