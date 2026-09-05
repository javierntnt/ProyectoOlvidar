package com.remindme.domain.use_case

import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType

/**
 * Per-type task validation (spec: Create Task — "REJECTED: empty name" + the
 * type-specific fields from Three Task Types):
 * - FIXED:  1..7 dayOfWeek and 0..1439 timeMinute.
 * - FLEXIBLE: name only.
 * - HABIT:  a frequency and a strictly positive target of hours.
 */
object TaskValidator {

    fun validate(
        name: String,
        type: TaskType,
        dayOfWeek: Int?,
        timeMinute: Int?,
        habitFrequency: HabitFrequency?,
        habitTargetHours: Int?,
    ): TaskInputError? {
        if (name.isBlank()) return TaskInputError.EMPTY_NAME
        return when (type) {
            TaskType.FIXED -> when {
                dayOfWeek == null || dayOfWeek !in 1..7 -> TaskInputError.INVALID_DAY
                timeMinute == null || timeMinute !in 0..1439 -> TaskInputError.INVALID_TIME
                else -> null
            }

            TaskType.FLEXIBLE -> null

            TaskType.HABIT -> when {
                habitFrequency == null -> TaskInputError.MISSING_HABIT_FREQUENCY
                habitTargetHours == null -> TaskInputError.MISSING_HABIT_TARGET
                habitTargetHours <= 0 -> TaskInputError.INVALID_HABIT_TARGET
                else -> null
            }
        }
    }
}