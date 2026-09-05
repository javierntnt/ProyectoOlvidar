package com.remindme.domain.use_case

import com.remindme.data.repository.TaskRepository

/**
 * Delete a task (spec: Delete Task). Deleting an unknown id is a no-op.
 * Cancelling the task's scheduled advance alert is the caller's job
 * (ReminderScheduler.cancelAdvanceAlert, wired by the UI in Phase 5).
 */
class DeleteTask(private val repository: TaskRepository) {

    suspend operator fun invoke(taskId: Long) = repository.delete(taskId)
}