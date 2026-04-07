package com.drivershield.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

object CycleCalculator {

    private const val CYCLE_LENGTH = 5
    private val WEEK5_OFF_DAYS = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

    fun getWeekNumberInCycle(date: LocalDate, cycleStartEpoch: Long): Int {
        if (cycleStartEpoch <= 0L) return 0

        val cycleStartDate = LocalDate.ofEpochDay(cycleStartEpoch / (24 * 60 * 60 * 1000))
        val weekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

        val startWeek = cycleStartDate.get(weekFields.weekOfWeekBasedYear()) +
            cycleStartDate.get(weekFields.weekBasedYear()) * 52
        val dateWeek = date.get(weekFields.weekOfWeekBasedYear()) +
            date.get(weekFields.weekBasedYear()) * 52

        val weeksDiff = (dateWeek - startWeek) % CYCLE_LENGTH
        return if (weeksDiff < 0) (weeksDiff + CYCLE_LENGTH) else weeksDiff + 1
    }

    fun isWeek5(date: LocalDate, cycleStartEpoch: Long): Boolean {
        if (cycleStartEpoch <= 0L) return false
        return getWeekNumberInCycle(date, cycleStartEpoch) == 5
    }

    fun isOffDay(date: LocalDate, cycleStartEpoch: Long, userOffDays: List<Int>): Boolean {
        if (cycleStartEpoch <= 0L) {
            return userOffDays.contains(date.dayOfWeek.value)
        }

        return if (isWeek5(date, cycleStartEpoch)) {
            WEEK5_OFF_DAYS.contains(date.dayOfWeek)
        } else {
            userOffDays.contains(date.dayOfWeek.value)
        }
    }

    fun getEffectiveOffDays(
        weekStart: LocalDate,
        cycleStartEpoch: Long,
        userOffDays: List<Int>
    ): List<DayOfWeek> {
        if (cycleStartEpoch <= 0L) {
            return userOffDays.map { DayOfWeek.of(it) }
        }

        return if (isWeek5(weekStart, cycleStartEpoch)) {
            WEEK5_OFF_DAYS
        } else {
            userOffDays.map { DayOfWeek.of(it) }
        }
    }

    fun getWorkDaysInWeek(
        weekStart: LocalDate,
        cycleStartEpoch: Long,
        userOffDays: List<Int>
    ): Int {
        return 7 - getEffectiveOffDays(weekStart, cycleStartEpoch, userOffDays).size
    }

    fun getNextWeek5Start(cycleStartEpoch: Long): LocalDate? {
        if (cycleStartEpoch <= 0L) return null

        val cycleStartDate = LocalDate.ofEpochDay(cycleStartEpoch / (24 * 60 * 60 * 1000))
        val today = LocalDate.now()
        val weekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

        val startWeek = cycleStartDate.get(weekFields.weekOfWeekBasedYear()) +
            cycleStartDate.get(weekFields.weekBasedYear()) * 52
        val todayWeek = today.get(weekFields.weekOfWeekBasedYear()) +
            today.get(weekFields.weekBasedYear()) * 52

        val weeksFromStart = todayWeek - startWeek
        val currentCyclePos = weeksFromStart % CYCLE_LENGTH
        val weeksUntilWeek5 = if (currentCyclePos < 4) (4 - currentCyclePos) else (CYCLE_LENGTH - currentCyclePos + 4)

        val week5Start = today.plusWeeks(weeksUntilWeek5.toLong()).with(DayOfWeek.MONDAY)
        return week5Start
    }

    fun getDaysUntilWeek5(cycleStartEpoch: Long): Long {
        val nextWeek5 = getNextWeek5Start(cycleStartEpoch) ?: return -1
        return ChronoUnit.DAYS.between(LocalDate.now(), nextWeek5)
    }

    fun isSundayBeforeWeek5(cycleStartEpoch: Long): Boolean {
        val nextWeek5 = getNextWeek5Start(cycleStartEpoch) ?: return false
        val tomorrow = LocalDate.now().plusDays(1)
        return nextWeek5 == tomorrow && tomorrow.dayOfWeek == DayOfWeek.MONDAY
    }
}
