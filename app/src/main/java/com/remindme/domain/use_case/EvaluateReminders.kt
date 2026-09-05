package com.remindme.domain.use_case

import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.DeliveryDecision
import com.remindme.domain.model.ReminderEvaluation
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.model.TaskType
import java.util.TimeZone

/**
 * Periodic-reminder evaluation (spec: Periodic Pending Reminders). Returns the
 * pending FLEXIBLE/HABIT tasks only when the anti-spam gate allows a delivery;
 * FIXED tasks keep their own exact-alarm pipeline and are never nudged here.
 */
class EvaluateReminders(
    private val taskRepository: TaskRepository,
    private val antiSpamGate: AntiSpamGate,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun evaluate(timeZone: TimeZone, nowEpochMillis: Long = clock()): ReminderEvaluation {
        val decision = antiSpamGate.canDeliver(ReminderKind.PENDING_REMINDER, nowEpochMillis, timeZone)
        val allowed = decision is DeliveryDecision.Allowed
        return ReminderEvaluation(
            allowed = allowed,
            blockedReason = (decision as? DeliveryDecision.Blocked)?.reason,
            pendingTasks = if (allowed) {
                taskRepository.getAllPending().filter { it.type != TaskType.FIXED }
            } else {
                emptyList()
            },
        )
    }
}