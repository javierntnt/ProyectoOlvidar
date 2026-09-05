package com.remindme.domain.use_case

import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult

/**
 * Create a task (spec: Create Task). Names are trimmed; a rejected input
 * returns [TaskWriteResult.Invalid] without touching the database.
 */
class CreateTask(private val repository: TaskRepository) {

    suspend operator fun invoke(
        name: String,
        type: TaskType,
        dayOfWeek: Int? = null,
        timeMinute: Int? = null,
        habitFrequency: HabitFrequency? = null,
        habitTargetHours: Int? = null,
        habitDoneHours: Int = 0,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): TaskWriteResult {
        val error = TaskValidator.validate(name, type, dayOfWeek, timeMinute, habitFrequency, habitTargetHours)
            ?: return save(
                Task(
                    name = name.trim(),
                    type = type,
                    dayOfWeek = dayOfWeek,
                    timeMinute = timeMinute,
                    habitFrequency = habitFrequency,
                    habitTargetHours = habitTargetHours,
                    habitDoneHours = habitDoneHours,
                    createdAt = nowEpochMillis,
                ),
            )
        return TaskWriteResult.Invalid(error)
    }

    private suspend fun save(task: Task): TaskWriteResult {
        val id = repository.save(task)
        return TaskWriteResult.Saved(task.copy(id = id))
    }
}