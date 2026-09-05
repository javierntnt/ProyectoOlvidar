package com.remindme.domain.use_case

import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task-validation rules (spec: Create Task — "REJECTED: empty name" plus the
 * type-specific field requirements from the Three Task Types requirement).
 */
class TaskValidatorTest {

    @Test
    fun `blank name is always rejected`() {
        assertEquals(
            TaskInputError.EMPTY_NAME,
            TaskValidator.validate("   ", TaskType.FIXED, 1, 540, null, null),
        )
        assertEquals(
            TaskInputError.EMPTY_NAME,
            TaskValidator.validate("", TaskType.FLEXIBLE, null, null, null, null),
        )
        assertEquals(
            TaskInputError.EMPTY_NAME,
            TaskValidator.validate("", TaskType.HABIT, 1, null, HabitFrequency.DAILY, 2),
        )
    }

    @Test
    fun `fixed tasks require a valid day and time`() {
        assertNull(TaskValidator.validate("Standup", TaskType.FIXED, 1, 540, null, null))
        assertEquals(TaskInputError.INVALID_DAY, TaskValidator.validate("X", TaskType.FIXED, null, 540, null, null))
        assertEquals(TaskInputError.INVALID_DAY, TaskValidator.validate("X", TaskType.FIXED, 0, 540, null, null))
        assertEquals(TaskInputError.INVALID_DAY, TaskValidator.validate("X", TaskType.FIXED, 8, 540, null, null))
        assertEquals(TaskInputError.INVALID_TIME, TaskValidator.validate("X", TaskType.FIXED, 1, null, null, null))
        assertEquals(TaskInputError.INVALID_TIME, TaskValidator.validate("X", TaskType.FIXED, 1, -1, null, null))
        assertEquals(TaskInputError.INVALID_TIME, TaskValidator.validate("X", TaskType.FIXED, 1, 1440, null, null))
    }

    @Test
    fun `flexible tasks need only a name`() {
        assertNull(TaskValidator.validate("Read", TaskType.FLEXIBLE, null, null, null, null))
    }

    @Test
    fun `habit tasks require a frequency and a positive target`() {
        assertNull(TaskValidator.validate("Exercise", TaskType.HABIT, 3, null, HabitFrequency.DAILY, 1))
        assertEquals(
            TaskInputError.MISSING_HABIT_FREQUENCY,
            TaskValidator.validate("X", TaskType.HABIT, 3, null, null, 1),
        )
        assertEquals(
            TaskInputError.MISSING_HABIT_TARGET,
            TaskValidator.validate("X", TaskType.HABIT, 3, null, HabitFrequency.WEEKLY, null),
        )
        assertEquals(
            TaskInputError.INVALID_HABIT_TARGET,
            TaskValidator.validate("X", TaskType.HABIT, 3, null, HabitFrequency.WEEKLY, 0),
        )
        assertEquals(
            TaskInputError.INVALID_HABIT_TARGET,
            TaskValidator.validate("X", TaskType.HABIT, 3, null, HabitFrequency.WEEKLY, -2),
        )
    }
}