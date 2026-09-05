package com.remindme.domain.model

/**
 * Result of the task write use cases (CreateTask / UpdateTask). Persisted
 * changes surface as [Saved]; anything rejected by [TaskInputError] surfaces as
 * [Invalid] and leaves the database untouched.
 */
sealed interface TaskWriteResult {
    data class Saved(val task: Task) : TaskWriteResult
    data class Invalid(val error: TaskInputError) : TaskWriteResult
}

/** Validation failures the UI maps to user-facing messages. */
enum class TaskInputError {
    EMPTY_NAME,
    INVALID_DAY,
    INVALID_TIME,
    MISSING_HABIT_FREQUENCY,
    MISSING_HABIT_TARGET,
    INVALID_HABIT_TARGET,
    TASK_NOT_FOUND,
}