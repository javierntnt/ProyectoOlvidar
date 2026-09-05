package com.remindme.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.remindme.R
import com.remindme.RemindMeApp
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import com.remindme.ui.theme.RemindMeTheme

/**
 * Week screen (task 5.1): seven-day window (Mon→Sun) with today highlighted,
 * tasks grouped by day and type (FIXED with time, HABIT with progress, plus an
 * "Anytime this week" section for FLEXIBLE tasks). Hosts [WeekViewModel] wired
 * to the app's manual DI container.
 */
@Composable
fun WeekScreen(onAddTask: () -> Unit, onEditTask: (Long) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RemindMeApp).container
    val viewModel: WeekViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WeekViewModel(
                    taskRepository = container.taskRepository,
                    updateTask = container.updateTask,
                    deleteTask = container.deleteTask,
                    scheduleAlarm = { task -> container.reminderScheduler.scheduleAdvanceAlert(task) },
                    cancelAlarm = { taskId -> container.reminderScheduler.cancelAdvanceAlert(taskId) },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    WeekViewContent(
        state = state,
        onToggleDone = viewModel::toggleDone,
        onDelete = viewModel::delete,
        onEdit = { onEditTask(it.id) },
        onAdd = onAddTask,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onToday = viewModel::goToCurrentWeek,
    )
}

/** Stateless week-view content (pure UI, unit-testable in Compose tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewContent(
    state: WeekUiState,
    onToggleDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onAdd: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.week_range, state.weekStartLabel, state.weekEndLabel))
                },
                navigationIcon = {
                    IconButton(onClick = onToday) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.week_today_button))
                    }
                },
                actions = {
                    IconButton(onClick = onPreviousWeek) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.week_previous))
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.week_next))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.week_add_task))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { WeekStrip(state) }

            if (state.anytimeTasks.isNotEmpty()) {
                item {
                    AnytimeSection(state.anytimeTasks, onToggleDone, onDelete, onEdit)
                }
            }

            items(state.days) { day ->
                DaySection(
                    day = day,
                    onToggleDone = onToggleDone,
                    onDelete = onDelete,
                    onEdit = onEdit,
                )
            }
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.week_delete_confirm_title)) },
            text = { Text(stringResource(R.string.week_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(task)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** The Mon..Sun strip with the current week day highlighted. */
@Composable
private fun WeekStrip(state: WeekUiState) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.days.forEach { day ->
                DayChip(day = day, isToday = day.isToday)
            }
        }
    }
}

@Composable
private fun DayChip(day: DayUi, isToday: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(40.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = WeekWindow.dayName(day.dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = WeekWindow.dayOfMonth(day.dateEpochMillis, java.util.TimeZone.getDefault()).toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun AnytimeSection(
    tasks: List<Task>,
    onToggleDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.week_anytime),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tasks.forEach { task ->
            TaskRow(task, onToggleDone, onDelete, onEdit)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DaySection(
    day: DayUi,
    onToggleDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
) {
    val hasTasks = day.fixedTasks.isNotEmpty() || day.habitTasks.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${WeekWindow.dayName(day.dayOfWeek)} ${WeekWindow.monthDayLabel(day.dateEpochMillis, java.util.TimeZone.getDefault())}",
                style = MaterialTheme.typography.titleMedium,
                color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (day.isToday) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = stringResource(R.string.week_today),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (!hasTasks) {
            Text(
                text = stringResource(R.string.week_no_tasks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            day.fixedTasks.forEach { task ->
                TaskRow(task, onToggleDone, onDelete, onEdit)
            }
            day.habitTasks.forEach { task ->
                TaskRow(task, onToggleDone, onDelete, onEdit)
            }
        }
        HorizontalDivider()
    }
}

/** One task row: done checkbox, name, type metadata (time / habit progress). */
@Composable
private fun TaskRow(
    task: Task,
    onToggleDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(task) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task) })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            when (task.type) {
                TaskType.FIXED ->
                    Text(
                        text = task.timeMinute?.let { WeekWindow.formatTime(it) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                TaskType.HABIT ->
                    Text(
                        text = stringResource(
                            R.string.week_habit_progress,
                            task.habitDoneHours,
                            task.habitTargetHours ?: 0,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                TaskType.FLEXIBLE -> Unit
            }
        }
        IconButton(onClick = { onEdit(task) }) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.week_edit_task),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(task) }) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.week_delete_task),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Preview of the week view with sample data. */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400)
@Composable
private fun WeekViewPreview() {
    RemindMeTheme {
        WeekViewContent(
            state = WeekUiState(
                weekStartEpochMillis = 0L,
                todayEpochMillis = 0L,
                weekStartLabel = "Sep 7",
                weekEndLabel = "Sep 13",
                anytimeTasks = emptyList(),
                days = listOf(
                    DayUi(
                        dayOfWeek = 1,
                        dateEpochMillis = 0L,
                        isToday = true,
                        fixedTasks = listOf(
                            Task(id = 1, name = "Standup", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 540, createdAt = 0L),
                        ),
                        habitTasks = listOf(
                            Task(id = 2, name = "Exercise", type = TaskType.HABIT, dayOfWeek = 1, habitFrequency = com.remindme.domain.model.HabitFrequency.DAILY, habitTargetHours = 2, habitDoneHours = 1, createdAt = 0L),
                        ),
                    ),
                    DayUi(dayOfWeek = 2, dateEpochMillis = 0L, isToday = false),
                ),
            ),
            onToggleDone = {},
            onDelete = {},
            onEdit = {},
            onAdd = {},
            onPreviousWeek = {},
            onNextWeek = {},
            onToday = {},
        )
    }
}