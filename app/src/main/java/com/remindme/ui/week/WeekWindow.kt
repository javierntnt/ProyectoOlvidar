package com.remindme.ui.week

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure helpers for the week view (task 5.1: Mon→Sun current week default).
 * Calendar-based and DST-aware, mirroring [com.remindme.domain.time.TimeUtils].
 * Domain day numbering is 1 = Monday .. 7 = Sunday.
 */
object WeekWindow {

    /**
     * Local Monday 00:00 of the week that contains [epochMillis] in [timeZone].
     */
    fun startOfWeek(epochMillis: Long, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Domain 1=Mon → subtract (dow-1) days to land on Monday.
        val domainDay = toDomainDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))
        if (domainDay > 1) calendar.add(Calendar.DAY_OF_YEAR, -(domainDay - 1))
        return calendar.timeInMillis
    }

    /**
     * Start of the [dayIndex]-th day of the displayed week ([dayIndex] 0 = Monday;
     * 7 = Monday of the NEXT week, used by [com.remindme.ui.week.WeekViewModel.nextWeek]).
     * Uses Calendar.add so DST transitions produce the correct local time.
     */
    fun dayStart(weekStart: Long, dayIndex: Int, timeZone: TimeZone): Long {
        require(dayIndex in 0..7) { "dayIndex must be 0..7, was $dayIndex" }
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = weekStart
        calendar.add(Calendar.DAY_OF_YEAR, dayIndex)
        return calendar.timeInMillis
    }

    /**
     * Shifts [epochMillis] by [days] calendar days (DST-safe via Calendar.add).
     */
    fun addDays(epochMillis: Long, days: Int, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.timeInMillis
    }

    /** Locale-aware short day name for a domain day (1=Mon .. 7=Sun), e.g. "Mon". */
    fun dayName(dayOfWeek: Int): String {
        require(dayOfWeek in 1..7) { "dayOfWeek must be 1..7, was $dayOfWeek" }
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
        return symbols.shortWeekdays[toCalendarDayOfWeek(dayOfWeek)]
    }

    /** Locale-aware month+day label for a date, e.g. "Sep 7". */
    fun monthDayLabel(epochMillis: Long, timeZone: TimeZone): String {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
        val month = symbols.shortMonths[calendar.get(Calendar.MONTH)]
        return "$month ${calendar.get(Calendar.DAY_OF_MONTH)}"
    }

    /** Day-of-month (1..31) of a date in [timeZone]. */
    fun dayOfMonth(epochMillis: Long, timeZone: TimeZone): Int {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = epochMillis
        return calendar.get(Calendar.DAY_OF_MONTH)
    }

    /** "HH:mm" (24 h) for a minutes-from-midnight value, e.g. 540 → "09:00". */
    fun formatTime(timeMinute: Int): String =
        String.format(Locale.US, "%02d:%02d", timeMinute / 60, timeMinute % 60)

    /** java.util.Calendar DAY_OF_WEEK (1 = Sunday) ← domain numbering (1 = Monday). */
    fun toCalendarDayOfWeek(domainDay: Int): Int =
        if (domainDay == 7) Calendar.SUNDAY else domainDay + 1

    /** java.util.Calendar DAY_OF_WEEK (1 = Sunday) → domain numbering (1 = Monday). */
    fun toDomainDayOfWeek(calendarDayOfWeek: Int): Int =
        if (calendarDayOfWeek == Calendar.SUNDAY) 7 else calendarDayOfWeek - 1
}