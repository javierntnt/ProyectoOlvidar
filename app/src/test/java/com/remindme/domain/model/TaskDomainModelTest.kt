package com.remindme.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDomainModelTest {

    @Test
    fun `FIXED task defaults are sane`() {
        val task = Task(
            id = 1,
            name = "Standup",
            type = TaskType.FIXED,
            dayOfWeek = 1,
            timeMinute = 540, // 09:00
            createdAt = 1_700_000_000_000L,
        )

        assertFalse(task.done)
        assertEquals(0, task.habitDoneHours)
        assertNull(task.habitFrequency)
        assertNull(task.habitTargetHours)
    }

    @Test
    fun `HABIT task carries frequency and target`() {
        val task = Task(
            id = 2,
            name = "Exercise",
            type = TaskType.HABIT,
            dayOfWeek = 3,
            habitFrequency = HabitFrequency.DAILY,
            habitTargetHours = 1,
            habitDoneHours = 0, // edge case: habit without progress shows 0 done (spec)
            createdAt = 1_700_000_000_000L,
        )

        assertEquals(HabitFrequency.DAILY, task.habitFrequency)
        assertEquals(1, task.habitTargetHours)
        assertEquals(0, task.habitDoneHours) // "habit without progress shows 0 done"
    }

    @Test
    fun `FLEXIBLE task has no day or time`() {
        val task = Task(
            id = 3,
            name = "Read",
            type = TaskType.FLEXIBLE,
            createdAt = 1_700_000_000_000L,
        )

        assertNull(task.dayOfWeek)
        assertNull(task.timeMinute)
        assertNull(task.habitFrequency)
    }

    @Test
    fun `ReminderKind values match spec categories`() {
        assertEquals(2, ReminderKind.entries.size)
        assertEquals("ADVANCE_ALERT", ReminderKind.ADVANCE_ALERT.name)
        assertEquals("PENDING_REMINDER", ReminderKind.PENDING_REMINDER.name)
    }
}