package com.remindme.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed user-controllable reminder settings.
 *
 * Defaults (resolved from the design's open questions):
 * - leadTimeMinutes = 0  → at time (open question 2: "reminder lead default = at time")
 * - reminderIntervalMinutes = 60  (minimum enforced by WorkManager at 15)
 * - dailyCap = 3  (spec: Anti-Spam Daily Cap default 3/day)
 * - cooldownMinutes = 240  (design: anti-spam gate default 4 h)
 * - quietStartMinute = 1380  (23:00)
 * - quietEndMinute = 420     (07:00)
 */
class ReminderPrefs(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "reminder_prefs")

    private object Keys {
        val LEAD_TIME_MINUTES       = intPreferencesKey("lead_time_minutes")
        val REMINDER_INTERVAL_MIN   = intPreferencesKey("reminder_interval_minutes")
        val DAILY_CAP               = intPreferencesKey("daily_cap")
        val COOLDOWN_MINUTES        = intPreferencesKey("cooldown_minutes")
        val QUIET_START_MINUTE      = intPreferencesKey("quiet_start_minute")
        val QUIET_END_MINUTE        = intPreferencesKey("quiet_end_minute")
    }

    // ---- defaults ------------------------------------------------------------

    companion object {
        const val DEFAULT_LEAD_TIME_MINUTES     = 0
        const val DEFAULT_REMINDER_INTERVAL_MIN = 60
        const val DEFAULT_DAILY_CAP             = 3
        const val DEFAULT_COOLDOWN_MINUTES      = 240   // 4 hours
        const val DEFAULT_QUIET_START_MINUTE    = 1380  // 23:00
        const val DEFAULT_QUIET_END_MINUTE      = 420   // 07:00
    }

    // ---- observable flows (use this in the settings screen) -----------------

    val leadTimeMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.LEAD_TIME_MINUTES] ?: DEFAULT_LEAD_TIME_MINUTES
    }

    val reminderIntervalMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.REMINDER_INTERVAL_MIN] ?: DEFAULT_REMINDER_INTERVAL_MIN
    }

    val dailyCap: Flow<Int> = context.dataStore.data.map {
        it[Keys.DAILY_CAP] ?: DEFAULT_DAILY_CAP
    }

    val cooldownMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.COOLDOWN_MINUTES] ?: DEFAULT_COOLDOWN_MINUTES
    }

    val quietStartMinute: Flow<Int> = context.dataStore.data.map {
        it[Keys.QUIET_START_MINUTE] ?: DEFAULT_QUIET_START_MINUTE
    }

    val quietEndMinute: Flow<Int> = context.dataStore.data.map {
        it[Keys.QUIET_END_MINUTE] ?: DEFAULT_QUIET_END_MINUTE
    }

    // ---- setters (called from Settings screen) -------------------------------

    suspend fun setLeadTimeMinutes(value: Int) = context.dataStore.edit {
        it[Keys.LEAD_TIME_MINUTES] = value
    }

    suspend fun setReminderIntervalMinutes(value: Int) = context.dataStore.edit {
        it[Keys.REMINDER_INTERVAL_MIN] = value.coerceAtLeast(15) // WorkManager minimum
    }

    suspend fun setDailyCap(value: Int) = context.dataStore.edit {
        it[Keys.DAILY_CAP] = value.coerceAtLeast(1)
    }

    suspend fun setCooldownMinutes(value: Int) = context.dataStore.edit {
        it[Keys.COOLDOWN_MINUTES] = value.coerceAtLeast(0)
    }

    suspend fun setQuietStartMinute(value: Int) = context.dataStore.edit {
        it[Keys.QUIET_START_MINUTE] = value.coerceIn(0, 1439)
    }

    suspend fun setQuietEndMinute(value: Int) = context.dataStore.edit {
        it[Keys.QUIET_END_MINUTE] = value.coerceIn(0, 1439)
    }
}