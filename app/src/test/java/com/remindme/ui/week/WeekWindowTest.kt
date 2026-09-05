package com.remindme.ui.week

import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for the week-window helpers used by the UI (task 5.1).
 * Locale is pinned to US so day/month names are deterministic.
 */
class WeekWindowTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-01-05 is a Monday; Jan 1 2026 is a Thursday.
    private val mondayMidnightUtc = 1_767_571_200_000L
    private val mondayTenAmUtc = 1_767_607_200_000L
    private val sundayMidnightUtc = mondayMidnightUtc + 6 * 86_400_000L

    private val savedLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    @Test
    fun `startOfWeek returns the monday of the containing week`() {
        assertEquals(mondayMidnightUtc, WeekWindow.startOfWeek(mondayTenAmUtc, utc))
        // Any day of the same week (here: Sunday) resolves to the same Monday.
        assertEquals(mondayMidnightUtc, WeekWindow.startOfWeek(sundayMidnightUtc + 12 * 3_600_000L, utc))
    }

    @Test
    fun `dayStart walks the week and is dst-safe via calendar add`() {
        assertEquals(mondayMidnightUtc, WeekWindow.dayStart(mondayMidnightUtc, 0, utc))
        assertEquals(mondayMidnightUtc + 86_400_000L, WeekWindow.dayStart(mondayMidnightUtc, 1, utc))
        assertEquals(mondayMidnightUtc + 6 * 86_400_000L, WeekWindow.dayStart(mondayMidnightUtc, 6, utc))
        // One week forward (used by nextWeek) lands on next Monday.
        assertEquals(
            mondayMidnightUtc + 7 * 86_400_000L,
            WeekWindow.dayStart(mondayMidnightUtc, 7, utc),
        )
    }

    @Test
    fun `dayName maps domain days to short names`() {
        assertEquals("Mon", WeekWindow.dayName(1))
        assertEquals("Wed", WeekWindow.dayName(3))
        assertEquals("Sun", WeekWindow.dayName(7))
    }

    @Test
    fun `monthDayLabel renders locale month and day`() {
        assertEquals("Jan 5", WeekWindow.monthDayLabel(mondayMidnightUtc, utc))
        assertEquals("Jan 11", WeekWindow.monthDayLabel(sundayMidnightUtc, utc))
    }

    @Test
    fun `dayOfMonth extracts the calendar day`() {
        assertEquals(5, WeekWindow.dayOfMonth(mondayMidnightUtc, utc))
        assertEquals(11, WeekWindow.dayOfMonth(sundayMidnightUtc, utc))
    }

    @Test
    fun `formatTime renders hh mm`() {
        assertEquals("00:00", WeekWindow.formatTime(0))
        assertEquals("09:00", WeekWindow.formatTime(540))
        assertEquals("23:59", WeekWindow.formatTime(1439))
    }

    @Test
    fun `toDomainDayOfWeek converts calendar numbering`() {
        assertEquals(1, WeekWindow.toDomainDayOfWeek(java.util.Calendar.MONDAY))
        assertEquals(7, WeekWindow.toDomainDayOfWeek(java.util.Calendar.SUNDAY))
    }
}