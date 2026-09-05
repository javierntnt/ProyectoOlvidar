package com.remindme.domain.time

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure-JVM time helpers used by the anti-spam gate (day boundaries) and the
 * advance-alarm scheduler (weekly occurrences). Calendar-based and DST-aware.
 */
object TimeUtils {

    /**
     * Local midnight (epoch millis) that contains [epochMillis] when interpreted
     * in [timeZone]. Used as the "since" bound of the daily-cap count.
     */
    fun startOfDay(epochMillis: Long, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** Minutes from local midnight (0..1439) for [epochMillis] in [timeZone]. */
    fun minuteOfDay(epochMillis: Long, timeZone: TimeZone): Int {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /**
     * Epoch millis of the next occurrence of a weekly task: domain [dayOfWeek]
     * (1 = Monday .. 7 = Sunday, matching the domain model) at [timeMinute]
     * (minutes from midnight), strictly after [fromEpochMillis] in [timeZone].
     * An occurrence exactly at the current time is treated as already passed.
     */
    fun nextOccurrence(dayOfWeek: Int, timeMinute: Int, fromEpochMillis: Long, timeZone: TimeZone): Long {
        require(dayOfWeek in 1..7) { "dayOfWeek must be 1..7, was $dayOfWeek" }
        require(timeMinute in 0..1439) { "timeMinute must be 0..1439, was $timeMinute" }

        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = fromEpochMillis

        val todayDomainDay = toDomainDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))
        val todayMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val daysAhead = when {
            dayOfWeek > todayDomainDay -> dayOfWeek - todayDomainDay
            dayOfWeek < todayDomainDay -> 7 - todayDomainDay + dayOfWeek
            timeMinute > todayMinute -> 0
            else -> 7
        }
        if (daysAhead > 0) calendar.add(Calendar.DAY_OF_YEAR, daysAhead)

        calendar.set(Calendar.HOUR_OF_DAY, timeMinute / 60)
        calendar.set(Calendar.MINUTE, timeMinute % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** java.util.Calendar DAY_OF_WEEK (1 = Sunday) → domain numbering (1 = Monday). */
    private fun toDomainDayOfWeek(calendarDayOfWeek: Int): Int =
        if (calendarDayOfWeek == Calendar.SUNDAY) 7 else calendarDayOfWeek - 1
}