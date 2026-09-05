package com.remindme.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remindme.RemindMeApp
import com.remindme.domain.model.ReminderKind
import java.util.TimeZone

/**
 * Periodic pending-reminder worker (task 4.4). Runs every 15 minutes, evaluates
 * the pending FLEXIBLE/HABIT tasks through the anti-spam gate, posts a single
 * summary notification on the PENDING_REMINDERS channel, records the delivery
 * in the ledger, and prunes old ledger rows.
 *
 * Without the runtime notification permission the worker simply does nothing
 * and reports success (the permission prompt handles onboarding).
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val app = applicationContext as RemindMeApp
            val container = app.container

            if (!NotificationPermissionHelper.hasNotificationPermission(applicationContext)) {
                return Result.success()
            }

            val evaluation = container.evaluateReminders.evaluate(TimeZone.getDefault())
            if (evaluation.allowed && evaluation.pendingTasks.isNotEmpty()) {
                if (container.reminderNotifier.postPendingReminders(evaluation.pendingTasks)) {
                    container.reminderRepository.recordDelivery(ReminderKind.PENDING_REMINDER, taskId = null)
                }
            }

            container.reminderRepository.prune(System.currentTimeMillis() - PRUNE_AGE_MILLIS)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private companion object {
        const val PRUNE_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}