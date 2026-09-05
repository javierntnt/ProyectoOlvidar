package com.remindme.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import com.remindme.domain.time.TimeUtils
import com.remindme.domain.use_case.DeleteTask
import com.remindme.domain.use_case.UpdateTask
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rendered day of the week view (task 5.1). */
data class DayUi(
    /** Domain 1..7 (Mon..Sun). */
    val dayOfWeek: Int,
    /** Local start of this day inside the displayed week. */
    val dateEpochMillis: Long,
    /** True when this day is the current calendar day. */
    val isToday: Boolean,
    /** FIXED tasks of this day, ordered by time. */
    val fixedTasks: List<Task> = emptyList(),
    /** HABIT tasks of this day, ordered by name. */
    val habitTasks: List<Task> = emptyList(),
)

/** Observable state of the week screen. */
data class WeekUiState(
    /** Local Monday 00:00 of the displayed week. */
    val weekStartEpochMillis: Long = 0L,
    /** Local midnight of the current calendar day (for the "today" cursor). */
    val todayEpochMillis: Long = 0L,
    /** Locale month+day label of the displayed week's Monday (e.g. "Sep 7"). */
    val weekStartLabel: String = "",
    /** Locale month+day label of the displayed week's Sunday (e.g. "Sep 13"). */
    val weekEndLabel: String = "",
    /** Tasks without an assigned day (FLEXIBLE "anytime this week" tasks). */
    val anytimeTasks: List<Task> = emptyList(),
    /** The seven rendered days (Mon..Sun). */
    val days: List<DayUi> = emptyList(),
) {
    val totalTasks: Int get() = anytimeTasks.size + days.sumOf { it.fixedTasks.size + it.habitTasks.size }
}

/**
 * Week view state holder (task 5.1). Combines the displayed-week cursor with the
 * observable task list; exposes completion/deletion actions that keep the
 * alarm scheduler in sync (tasks 5.4 + spec Delete Task / toggling done).
 *
 * [scheduleAlarm] re-arms an advance alert for a FIXED task kept pending;
 * [cancelAlarm] drops the alarm of a completed or deleted task. Both are wired
 * to [com.remindme.notifications.ReminderScheduler] by the UI layer so this
 * class stays unit-testable without Android services.
 */
class WeekViewModel(
    private val taskRepository: TaskRepository,
    private val updateTask: UpdateTask,
    private val deleteTask: DeleteTask,
    private val scheduleAlarm: suspend (Task) -> Unit,
    private val cancelAlarm: (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : ViewModel() {

    private val displayedWeekStart = MutableStateFlow(WeekWindow.startOfWeek(clock(), timeZone))

    val uiState: StateFlow<WeekUiState> = combine(
        displayedWeekStart,
        taskRepository.observeAll(),
    ) { weekStart, tasks -> buildState(weekStart, tasks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    private fun buildState(weekStart: Long, tasks: List<Task>): WeekUiState {
        val todayStart = TimeUtils.startOfDay(clock(), timeZone)
        val days = (0..6).map { index ->
            val dateStart = WeekWindow.dayStart(weekStart, index, timeZone)
            val dayTasks = tasks.filter { it.dayOfWeek == index + 1 }
            DayUi(
                dayOfWeek = index + 1,
                dateEpochMillis = dateStart,
                isToday = dateStart == todayStart,
                fixedTasks = dayTasks
                    .filter { it.type == TaskType.FIXED }
                    .sortedBy { it.timeMinute ?: Int.MAX_VALUE },
                habitTasks = dayTasks
                    .filter { it.type == TaskType.HABIT }
                    .sortedBy { it.name },
            )
        }
        return WeekUiState(
            weekStartEpochMillis = weekStart,
            todayEpochMillis = todayStart,
            weekStartLabel = WeekWindow.monthDayLabel(weekStart, timeZone),
            weekEndLabel = WeekWindow.monthDayLabel(
                WeekWindow.dayStart(weekStart, 6, timeZone),
                timeZone,
            ),
            // FLEXIBLE tasks have no assigned day; they live in one "anytime" section.
            anytimeTasks = tasks.filter { it.dayOfWeek == null }.sortedBy { it.name },
            days = days,
        )
    }

    // ---- week navigation -----------------------------------------------------

    fun nextWeek() {
        displayedWeekStart.value =
            WeekWindow.dayStart(displayedWeekStart.value, 7, timeZone)
    }

    fun previousWeek() {
        displayedWeekStart.value =
            WeekWindow.addDays(displayedWeekStart.value, -7, timeZone)
    }

    fun goToCurrentWeek() {
        displayedWeekStart.value = WeekWindow.startOfWeek(clock(), timeZone)
    }

    // ---- task actions --------------------------------------------------------

    /**
     * Complete / reopen a task (spec: Delete Task also demands cancelling the
     * scheduled notifications — completing a FIXED task does the same so a done
     * task never fires an alarm).
     */
    fun toggleDone(task: Task) {
        viewModelScope.launch {
            val result = updateTask(task.copy(done = !task.done))
            if (result is TaskWriteResult.Saved) {
                val saved = result.task
                if (saved.type == TaskType.FIXED) {
                    if (saved.done) cancelAlarm(saved.id) else scheduleAlarm(saved)
                }
            }
            // Invalid results leave the persisted state untouched; the observed
            // flow refreshes the UI automatically.
        }
    }

    /** Delete a task and cancel its scheduled advance alert (task 5.4). */
    fun delete(task: Task) {
        viewModelScope.launch {
            deleteTask(task.id)
            cancelAlarm(task.id)
        }
    }
}