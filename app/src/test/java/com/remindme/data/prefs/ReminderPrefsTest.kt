package com.remindme.data.prefs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DataStore-backed reminder settings tests (task 2.3, spec User-Controlled
 * Intervals). Robolectric supplies a real Application context with a writable
 * files dir, so DataStore persists to disk exactly as it does on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ReminderPrefsTest {

    private lateinit var prefs: ReminderPrefs

    @Before
    fun setUp() {
        prefs = ReminderPrefs(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun `defaults match design open-question resolutions`() = runTest {
        assertEquals(ReminderPrefs.DEFAULT_LEAD_TIME_MINUTES, prefs.leadTimeMinutes.first())      // 0 → at time
        assertEquals(ReminderPrefs.DEFAULT_REMINDER_INTERVAL_MIN, prefs.reminderIntervalMinutes.first())
        assertEquals(ReminderPrefs.DEFAULT_DAILY_CAP, prefs.dailyCap.first())                      // spec: 3/day
        assertEquals(ReminderPrefs.DEFAULT_COOLDOWN_MINUTES, prefs.cooldownMinutes.first())        // design: 4h
        assertEquals(ReminderPrefs.DEFAULT_QUIET_START_MINUTE, prefs.quietStartMinute.first())     // 23:00
        assertEquals(ReminderPrefs.DEFAULT_QUIET_END_MINUTE, prefs.quietEndMinute.first())         // 07:00
    }

    @Test
    fun `setters persist values and round-trip through DataStore`() = runTest {
        prefs.setLeadTimeMinutes(30)
        prefs.setReminderIntervalMinutes(120)
        prefs.setDailyCap(5)
        prefs.setCooldownMinutes(60)
        prefs.setQuietStartMinute(1320)  // 22:00
        prefs.setQuietEndMinute(360)     // 06:00

        assertEquals(30, prefs.leadTimeMinutes.first())
        assertEquals(120, prefs.reminderIntervalMinutes.first())
        assertEquals(5, prefs.dailyCap.first())
        assertEquals(60, prefs.cooldownMinutes.first())
        assertEquals(1320, prefs.quietStartMinute.first())
        assertEquals(360, prefs.quietEndMinute.first())
    }

    @Test
    fun `clamped setters enforce sane bounds`() = runTest {
        prefs.setReminderIntervalMinutes(1)   // below WorkManager 15-min floor
        prefs.setDailyCap(-5)                 // below 1
        prefs.setQuietStartMinute(10_000)     // above 1439

        assertEquals(15, prefs.reminderIntervalMinutes.first())
        assertEquals(1, prefs.dailyCap.first())
        assertEquals(1439, prefs.quietStartMinute.first())
    }
}