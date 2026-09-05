package com.remindme.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType

/**
 * Room entity for the `tasks` table — a single-table design with nullable
 * type-specific columns (DayOfWeek+time for FIXED, frequency+target for HABIT).
 *
 * Room natively maps enum types to their `name` String since Room 2.3.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TaskType,
    /** 1–7 (Mon–Sun) — FIXED & HABIT only. */
    val dayOfWeek: Int? = null,
    /** Minutes from midnight — FIXED only. */
    val timeMinute: Int? = null,
    /** HABIT only: DAILY or WEEKLY. */
    val habitFrequency: HabitFrequency? = null,
    /** HABIT only: target hours per period. */
    val habitTargetHours: Int? = null,
    /** HABIT only: completed hours (manual stepper default 0). */
    val habitDoneHours: Int = 0,
    /** Task marked done for the current period. */
    val done: Boolean = false,
    /** Epoch millis. */
    val createdAt: Long,
) {
    fun toDomain(): Task = Task(
        id = id,
        name = name,
        type = type,
        dayOfWeek = dayOfWeek,
        timeMinute = timeMinute,
        habitFrequency = habitFrequency,
        habitTargetHours = habitTargetHours,
        habitDoneHours = habitDoneHours,
        done = done,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            name = task.name,
            type = task.type,
            dayOfWeek = task.dayOfWeek,
            timeMinute = task.timeMinute,
            habitFrequency = task.habitFrequency,
            habitTargetHours = task.habitTargetHours,
            habitDoneHours = task.habitDoneHours,
            done = task.done,
            createdAt = task.createdAt,
        )
    }
}