package com.remindme.ui.task

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.remindme.R
import com.remindme.RemindMeApp
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.ui.week.WeekWindow

/**
 * Create/edit task screen (task 5.2). [TaskFormScreen] wires [TaskFormViewModel]
 * to the DI container; [TaskFormContent] is the stateless UI.
 */
@Composable
fun TaskFormScreen(taskId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RemindMeApp).container
    val viewModel: TaskFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TaskFormViewModel(
                    createTask = container.createTask,
                    updateTask = container.updateTask,
                    taskRepository = container.taskRepository,
                    scheduleAlarm = { task -> container.reminderScheduler.scheduleAdvanceAlert(task) },
                    cancelAlarm = { id -> container.reminderScheduler.cancelAdvanceAlert(id) },
                    taskId = taskId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    TaskFormContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onDayChange = viewModel::onDayChange,
        onTimeChange = viewModel::onTimeChange,
        onFrequencyChange = viewModel::onFrequencyChange,
        onTargetChange = viewModel::onTargetChange,
        onDoneHoursChange = viewModel::onDoneHoursChange,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

/** Stateless task form UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormContent(
    state: TaskFormUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (TaskType) -> Unit,
    onDayChange: (Int) -> Unit,
    onTimeChange: (Int) -> Unit,
    onFrequencyChange: (HabitFrequency) -> Unit,
    onTargetChange: (Int) -> Unit,
    onDoneHoursChange: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.createdAt > 0L) R.string.task_form_edit_title else R.string.task_form_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.isSaving) {
                        Text(stringResource(R.string.task_form_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.task_form_name)) },
                isError = state.error != null,
                supportingText = {
                    state.error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- type selector -------------------------------------------------
            Text(stringResource(R.string.task_form_type), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(typeChipLabel(type)) },
                    )
                }
            }

            if (state.type == TaskType.FIXED) {
                // ---- day + time (fixed) ------------------------------------------
                Text(stringResource(R.string.task_form_day), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = state.dayOfWeek == day,
                            onClick = { onDayChange(day) },
                            label = { Text(WeekWindow.dayName(day)) },
                        )
                    }
                }

                Text(stringResource(R.string.task_form_time), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text(
                        state.timeMinute?.let { WeekWindow.formatTime(it) }
                            ?: stringResource(R.string.task_form_pick_time),
                    )
                }
            }

            if (state.type == TaskType.HABIT) {
                // ---- frequency -----------------------------------------------------
                Text(stringResource(R.string.task_form_frequency), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitFrequency.entries.forEach { frequency ->
                        FilterChip(
                            selected = state.habitFrequency == frequency,
                            onClick = { onFrequencyChange(frequency) },
                            label = { Text(frequencyChipLabel(frequency)) },
                        )
                    }
                }

                StepperRow(
                    label = stringResource(R.string.task_form_target_hours),
                    value = state.habitTargetHours,
                    onDecrease = { onTargetChange(state.habitTargetHours - 1) },
                    onIncrease = { onTargetChange(state.habitTargetHours + 1) },
                )
                StepperRow(
                    label = stringResource(R.string.task_form_done_hours),
                    value = state.habitDoneHours,
                    onDecrease = { onDoneHoursChange(state.habitDoneHours - 1) },
                    onIncrease = { onDoneHoursChange(state.habitDoneHours + 1) },
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialMinute = state.timeMinute ?: 540,
            onConfirm = {
                onTimeChange(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** A labeled [- value +] stepper used for habit hours and settings. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDecrease) { Text("-") }
            Text("$value", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onIncrease) { Text("+") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
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

@Composable
private fun typeChipLabel(type: TaskType): String = stringResource(
    when (type) {
        TaskType.FIXED -> R.string.task_form_fixed
        TaskType.FLEXIBLE -> R.string.task_form_flexible
        TaskType.HABIT -> R.string.task_form_habit
    },
)

@Composable
private fun frequencyChipLabel(frequency: HabitFrequency): String = stringResource(
    when (frequency) {
        HabitFrequency.DAILY -> R.string.task_form_daily
        HabitFrequency.WEEKLY -> R.string.task_form_weekly
    },
)

@Composable
fun errorMessage(error: TaskInputError): String = stringResource(
    when (error) {
        TaskInputError.EMPTY_NAME -> R.string.error_empty_name
        TaskInputError.INVALID_DAY -> R.string.error_invalid_day
        TaskInputError.INVALID_TIME -> R.string.error_invalid_time
        TaskInputError.MISSING_HABIT_FREQUENCY -> R.string.error_missing_habit_frequency
        TaskInputError.MISSING_HABIT_TARGET -> R.string.error_missing_habit_target
        TaskInputError.INVALID_HABIT_TARGET -> R.string.error_invalid_habit_target
        TaskInputError.TASK_NOT_FOUND -> R.string.error_task_not_found
    },
)