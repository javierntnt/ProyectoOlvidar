package com.remindme.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.remindme.data.prefs.ReminderPrefs
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import com.remindme.domain.time.TimeUtils
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Schedules fixed-task advance alerts (task 4.2) and the periodic pending
 * reminder worker (task 4.4).
 *
 * Advance alerts use `setExactAndAllowWhileIdle` when the Android 12+ special
 * access is granted and fall back to the inexact `setAndAllowWhileIdle`
 * otherwise. The periodic worker runs every 15 minutes (WorkManager minimum)
 * regardless of the DataStore interval; the interval only feeds the anti-spam
 * cooldown.
 */
class ReminderScheduler(
    private val context: Context,
    private val reminderPrefs: ReminderPrefs,
    private val taskRepository: TaskRepository,
) {

    /** (Re)schedule the advance alert of a FIXED task: next occurrence minus the lead time. */
    suspend fun scheduleAdvanceAlert(task: Task) {
        if (task.type != TaskType.FIXED) return
        val dayOfWeek = task.dayOfWeek ?: return
        val timeMinute = task.timeMinute ?: return

        val leadMinutes = reminderPrefs.leadTimeMinutes.first()
        var triggerAt = TimeUtils.nextOccurrence(
            dayOfWeek,
            timeMinute,
            System.currentTimeMillis(),
            TimeZone.getDefault(),
        )
        if (leadMinutes > 0) triggerAt -= leadMinutes * 60_000L

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = advanceAlertIntent(task.id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (ExactAlarmPermissionHelper.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Re-arm the alarms for every pending FIXED task (app start / after edits). */
    suspend fun rescheduleAdvanceAlerts() {
        taskRepository.getAllPending()
            .filter { it.type == TaskType.FIXED }
            .forEach { scheduleAdvanceAlert(it) }
    }

    /** Cancel the advance alert of a deleted task (safe no-op when none is armed). */
    fun cancelAdvanceAlert(taskId: Long) {
        val pendingIntent = advanceAlertIntent(taskId, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }

    /** Ensure the periodic pending-reminder worker exists (idempotent, UPDATE policy). */
    fun ensurePeriodicReminders() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun advanceAlertIntent(taskId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "periodic-pending-reminders"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}