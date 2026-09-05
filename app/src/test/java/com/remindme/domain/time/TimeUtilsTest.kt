package com.remindme.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/**
 * Pure-JVM unit tests for the time helpers used by the domain layer
 * (anti-spam day boundaries and advance-alarm scheduling).
 */
class TimeUtilsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-01-05T00:00:00Z is the start of Monday 2026-01-05 (Jan 1 2026 is a Thursday).
    private val mondayMidnightUtc = 1_767_571_200_000L
    private val mondayTenAmUtc = 1_767_607_200_000L // 2026-01-05T10:00:00Z

    @Test
    fun `startOfDay returns local midnight`() {
        assertEquals(mondayMidnightUtc, TimeUtils.startOfDay(mondayTenAmUtc, utc))
        assertEquals(mondayMidnightUtc, TimeUtils.startOfDay(mondayMidnightUtc + 86_399_999L, utc))
    }

    @Test
    fun `minuteOfDay extracts minutes since midnight`() {
        assertEquals(600, TimeUtils.minuteOfDay(mondayTenAmUtc, utc))
        assertEquals(0, TimeUtils.minuteOfDay(mondayMidnightUtc, utc))
        assertEquals(1439, TimeUtils.minuteOfDay(mondayMidnightUtc + 86_399_999L, utc))
    }

    @Test
    fun `nextOccurrence stays on the same day when the target time is still ahead`() {
        // Monday 10:00 → Monday 14:00 (same day, +4 h).
        assertEquals(mondayTenAmUtc + 14_400_000L, TimeUtils.nextOccurrence(1, 840, mondayTenAmUtc, utc))
    }

    @Test
    fun `nextOccurrence rolls to next week when the target time passed or is equal`() {
        // Monday 10:00 → Monday 09:30 next week (7 days minus 30 minutes).
        assertEquals(mondayTenAmUtc + 603_000_000L, TimeUtils.nextOccurrence(1, 570, mondayTenAmUtc, utc))
        // Monday 10:00 → Monday 10:00 next week (exact match is not "ahead").
        assertEquals(mondayTenAmUtc + 604_800_000L, TimeUtils.nextOccurrence(1, 600, mondayTenAmUtc, utc))
    }

    @Test
    fun `nextOccurrence moves to a later day within the same week`() {
        // Monday 10:00 → Wednesday 10:00 (+2 days).
        assertEquals(mondayTenAmUtc + 172_800_000L, TimeUtils.nextOccurrence(3, 600, mondayTenAmUtc, utc))
        // Monday 10:00 → Sunday 08:00 (+6 days but 2 hours earlier).
        assertEquals(mondayTenAmUtc + 511_200_000L, TimeUtils.nextOccurrence(7, 480, mondayTenAmUtc, utc))
    }
}