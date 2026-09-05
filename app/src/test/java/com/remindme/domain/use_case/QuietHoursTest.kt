package com.remindme.domain.use_case

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quiet-hours gate tests (spec: Quiet Hours — "REJECTED: posted during quiet hours").
 * Task 3.4 / 6.1 — RED-first for the quiet-hours policy.
 */
class QuietHoursTest {

    @Test
    fun `same-day window uses an inclusive start and an exclusive end`() {
        // 08:00–20:00
        assertFalse(QuietHours.isInsideQuietWindow(479, 480, 1200))  // 07:59
        assertTrue(QuietHours.isInsideQuietWindow(480, 480, 1200))   // 08:00
        assertTrue(QuietHours.isInsideQuietWindow(1199, 480, 1200))  // 19:59
        assertFalse(QuietHours.isInsideQuietWindow(1200, 480, 1200)) // 20:00
    }

    @Test
    fun `overnight window wraps past midnight`() {
        val start = 1380 // 23:00
        val end = 420    // 07:00
        assertFalse(QuietHours.isInsideQuietWindow(1379, start, end)) // 22:59
        assertTrue(QuietHours.isInsideQuietWindow(1380, start, end))  // 23:00
        assertTrue(QuietHours.isInsideQuietWindow(60, start, end))    // 01:00
        assertTrue(QuietHours.isInsideQuietWindow(419, start, end))   // 06:59
        assertFalse(QuietHours.isInsideQuietWindow(420, start, end))  // 07:00
        assertFalse(QuietHours.isInsideQuietWindow(600, start, end))  // 10:00
    }

    @Test(expected = IllegalArgumentException::class)
    fun `minute values outside the day are rejected`() {
        QuietHours.isInsideQuietWindow(600, -1, 420)
    }
}