package com.remindme.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.data.prefs.ReminderPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Editable reminder settings (task 5.3, spec User-Controlled Intervals). */
data class SettingsUiState(
    val leadTimeMinutes: Int = ReminderPrefs.DEFAULT_LEAD_TIME_MINUTES,
    val reminderIntervalMinutes: Int = ReminderPrefs.DEFAULT_REMINDER_INTERVAL_MIN,
    val dailyCap: Int = ReminderPrefs.DEFAULT_DAILY_CAP,
    val cooldownMinutes: Int = ReminderPrefs.DEFAULT_COOLDOWN_MINUTES,
    val quietStartMinute: Int = ReminderPrefs.DEFAULT_QUIET_START_MINUTE,
    val quietEndMinute: Int = ReminderPrefs.DEFAULT_QUIET_END_MINUTE,
)

/**
 * Settings screen state holder (task 5.3). Mirrors [ReminderPrefs] into a single
 * observable state; every mutation is persisted immediately through DataStore,
 * so the anti-spam config provider picks it up without a restart.
 */
class SettingsViewModel(private val prefs: ReminderPrefs) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.leadTimeMinutes,
        prefs.reminderIntervalMinutes,
        prefs.dailyCap,
        prefs.cooldownMinutes,
        prefs.quietStartMinute,
        prefs.quietEndMinute,
    ) { values: Array<Int> ->
        SettingsUiState(
            leadTimeMinutes = values[0],
            reminderIntervalMinutes = values[1],
            dailyCap = values[2],
            cooldownMinutes = values[3],
            quietStartMinute = values[4],
            quietEndMinute = values[5],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setLeadTimeMinutes(value: Int) = launch { prefs.setLeadTimeMinutes(value.coerceIn(0, 120)) }
    fun setReminderIntervalMinutes(value: Int) = launch { prefs.setReminderIntervalMinutes(value.coerceIn(15, 240)) }
    fun setDailyCap(value: Int) = launch { prefs.setDailyCap(value.coerceIn(1, 10)) }
    fun setCooldownMinutes(value: Int) = launch { prefs.setCooldownMinutes(value.coerceIn(0, 480)) }
    fun setQuietStartMinute(value: Int) = launch { prefs.setQuietStartMinute(value) }
    fun setQuietEndMinute(value: Int) = launch { prefs.setQuietEndMinute(value) }

    private inline fun launch(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}