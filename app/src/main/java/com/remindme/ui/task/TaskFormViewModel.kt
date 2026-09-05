package com.remindme.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import com.remindme.domain.use_case.CreateTask
import com.remindme.domain.use_case.UpdateTask
import com.remindme.ui.week.WeekWindow
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Draft state of the create/edit task form (task 5.2). */
data class TaskFormUiState(
    val name: String = "",
    val type: TaskType = TaskType.FIXED,
    /** 1..7 (Mon..Sun); null until a day is picked (or FLEXIBLE). */
    val dayOfWeek: Int? = null,
    /** Minutes from midnight; FIXED only. */
    val timeMinute: Int? = null,
    val habitFrequency: HabitFrequency = HabitFrequency.DAILY,
    val habitTargetHours: Int = 1,
    val habitDoneHours: Int = 0,
    /** Preserved fields when editing an existing task. */
    val done: Boolean = false,
    val createdAt: Long = 0L,
    /** Validation failure surfaced inline (spec: REJECTED empty name). */
    val error: TaskInputError? = null,
    val isSaving: Boolean = false,
    /** Edit mode: the existing task finished loading. */
    val loaded: Boolean = false,
    /** True after a successful save; the screen navigates back. */
    val saved: Boolean = false,
)

/**
 * Create/edit form state holder (task 5.2). Validates through the domain use
 * cases ([CreateTask]/[UpdateTask], spec "REJECTED: empty name") and keeps the
 * alarm scheduler in sync: saving a FIXED task cancels any stale alarm and
 * schedules the advance alert; saving any other type only cancels.
 */
class TaskFormViewModel(
    private val createTask: CreateTask,
    private val updateTask: UpdateTask,
    private val taskRepository: TaskRepository,
    private val scheduleAlarm: suspend (Task) -> Unit,
    private val cancelAlarm: (Long) -> Unit,
    private val taskId: Long?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskFormUiState())
    val uiState: StateFlow<TaskFormUiState> = _uiState.asStateFlow()

    init {
        if (taskId == null) {
            // New task: sensible defaults (today + 09:00) so only the name gates saving.
            _uiState.update {
                it.copy(dayOfWeek = todayDomainDay(), timeMinute = 540, loaded = true)
            }
        } else {
            loadTask(taskId)
        }
    }

    private fun loadTask(id: Long) {
        viewModelScope.launch {
            val task = taskRepository.getById(id)
            if (task == null) {
                _uiState.update { it.copy(error = TaskInputError.TASK_NOT_FOUND, loaded = true) }
            } else {
                _uiState.value = TaskFormUiState(
                    name = task.name,
                    type = task.type,
                    dayOfWeek = task.dayOfWeek,
                    timeMinute = task.timeMinute,
                    habitFrequency = task.habitFrequency ?: HabitFrequency.DAILY,
                    habitTargetHours = task.habitTargetHours ?: 1,
                    habitDoneHours = task.habitDoneHours,
                    done = task.done,
                    createdAt = task.createdAt,
                    loaded = true,
                )
            }
        }
    }

    // ---- input handlers ------------------------------------------------------

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun onTypeChange(type: TaskType) {
        _uiState.update {
            it.copy(
                type = type,
                dayOfWeek = if (type != TaskType.FLEXIBLE) (it.dayOfWeek ?: todayDomainDay()) else null,
                timeMinute = if (type == TaskType.FIXED) (it.timeMinute ?: 540) else null,
                error = null,
            )
        }
    }

    fun onDayChange(day: Int) {
        _uiState.update { it.copy(dayOfWeek = day, error = null) }
    }

    fun onTimeChange(timeMinute: Int) {
        _uiState.update { it.copy(timeMinute = timeMinute, error = null) }
    }

    fun onFrequencyChange(frequency: HabitFrequency) {
        _uiState.update { it.copy(habitFrequency = frequency, error = null) }
    }

    fun onTargetChange(target: Int) {
        _uiState.update { it.copy(habitTargetHours = target.coerceIn(1, 99), error = null) }
    }

    fun onDoneHoursChange(done: Int) {
        _uiState.update { it.copy(habitDoneHours = done.coerceIn(0, 99), error = null) }
    }

    // ---- persistence ---------------------------------------------------------

    fun save() {
        val current = _uiState.value
        if (current.isSaving || !current.loaded || current.error == TaskInputError.TASK_NOT_FOUND) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val state = _uiState.value
            val result = if (taskId == null) {
                createTask(
                    name = state.name,
                    type = state.type,
                    dayOfWeek = state.dayOfWeek,
                    timeMinute = state.timeMinute,
                    habitFrequency = state.habitFrequency,
                    habitTargetHours = state.habitTargetHours,
                    habitDoneHours = state.habitDoneHours,
                )
            } else {
                updateTask(
                    Task(
                        id = taskId,
                        name = state.name.trim(),
                        type = state.type,
                        dayOfWeek = state.dayOfWeek,
                        timeMinute = state.timeMinute,
                        habitFrequency = state.habitFrequency,
                        habitTargetHours = state.habitTargetHours,
                        habitDoneHours = state.habitDoneHours,
                        done = state.done,
                        createdAt = state.createdAt,
                    ),
                )
            }

            when (result) {
                is TaskWriteResult.Saved -> {
                    val saved = result.task
                    // Drop any stale alarm (type switch / edited time), then arm
                    // the advance alert only for FIXED tasks.
                    cancelAlarm(saved.id)
                    if (saved.type == TaskType.FIXED) scheduleAlarm(saved)
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }

                is TaskWriteResult.Invalid -> {
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
                }
            }
        }
    }

    private fun todayDomainDay(): Int {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = clock()
        return WeekWindow.toDomainDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))
    }
}