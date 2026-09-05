package com.remindme.domain.use_case

import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskWriteResult

/**
 * Edit a task (spec: Edit Task). Re-validates the full input, refuses updates
 * for tasks that no longer exist, and preserves [Task.createdAt].
 */
class UpdateTask(private val repository: TaskRepository) {

    suspend operator fun invoke(task: Task): TaskWriteResult {
        val error = TaskValidator.validate(
            task.name,
            task.type,
            task.dayOfWeek,
            task.timeMinute,
            task.habitFrequency,
            task.habitTargetHours,
        )
        if (error != null) return TaskWriteResult.Invalid(error)
        if (repository.getById(task.id) == null) return TaskWriteResult.Invalid(TaskInputError.TASK_NOT_FOUND)

        repository.save(task) // upsert (REPLACE) keeps the same id.
        return TaskWriteResult.Saved(repository.getById(task.id)!!)
    }
}