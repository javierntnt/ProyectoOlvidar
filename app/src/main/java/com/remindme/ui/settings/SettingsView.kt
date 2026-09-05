package com.remindme.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.remindme.R
import com.remindme.RemindMeApp
import com.remindme.ui.task.StepperRow
import com.remindme.ui.week.WeekWindow

/** Settings screen (task 5.3): reminder intervals/caps/quiet-hours prefs. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as RemindMeApp).container
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.reminderPrefs) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    SettingsContent(
        state = state,
        onLeadTimeChange = viewModel::setLeadTimeMinutes,
        onIntervalChange = viewModel::setReminderIntervalMinutes,
        onDailyCapChange = viewModel::setDailyCap,
        onCooldownChange = viewModel::setCooldownMinutes,
        onQuietStartChange = viewModel::setQuietStartMinute,
        onQuietEndChange = viewModel::setQuietEndMinute,
    )
}

/** Stateless settings UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onLeadTimeChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDailyCapChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onQuietStartChange: (Int) -> Unit,
    onQuietEndChange: (Int) -> Unit,
) {
    var picker by remember { mutableStateOf<QuietPicker?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(R.string.settings_advance_alerts)
            StepperRow(
                label = stringResource(R.string.settings_lead_time),
                value = state.leadTimeMinutes,
                onDecrease = { onLeadTimeChange(state.leadTimeMinutes - 5) },
                onIncrease = { onLeadTimeChange(state.leadTimeMinutes + 5) },
            )
            Text(
                text = stringResource(R.string.settings_lead_time_value, state.leadTimeMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            SectionTitle(R.string.settings_periodic)
            StepperRow(
                label = stringResource(R.string.settings_interval),
                value = state.reminderIntervalMinutes,
                onDecrease = { onIntervalChange(state.reminderIntervalMinutes - 15) },
                onIncrease = { onIntervalChange(state.reminderIntervalMinutes + 15) },
            )
            StepperRow(
                label = stringResource(R.string.settings_daily_cap),
                value = state.dailyCap,
                onDecrease = { onDailyCapChange(state.dailyCap - 1) },
                onIncrease = { onDailyCapChange(state.dailyCap + 1) },
            )
            StepperRow(
                label = stringResource(R.string.settings_cooldown),
                value = state.cooldownMinutes,
                onDecrease = { onCooldownChange(state.cooldownMinutes - 15) },
                onIncrease = { onCooldownChange(state.cooldownMinutes + 15) },
            )

            HorizontalDivider()

            SectionTitle(R.string.settings_quiet_hours)
            OutlinedButton(onClick = { picker = QuietPicker.START }) {
                Text(stringResource(R.string.settings_quiet_start, WeekWindow.formatTime(state.quietStartMinute)))
            }
            OutlinedButton(onClick = { picker = QuietPicker.END }) {
                Text(stringResource(R.string.settings_quiet_end, WeekWindow.formatTime(state.quietEndMinute)))
            }
        }
    }

    picker?.let { which ->
        val current = if (which == QuietPicker.START) state.quietStartMinute else state.quietEndMinute
        QuietTimePickerDialog(
            initialMinute = current,
            onConfirm = {
                if (which == QuietPicker.START) onQuietStartChange(it) else onQuietEndChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun SectionTitle(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private enum class QuietPicker { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimePickerDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour * 60 + timePickerState.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = { TimePicker(state = timePickerState) },
    )
}