package com.remindme.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remindme.RemindMeApp
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.model.TaskType
import com.remindme.domain.time.TimeUtils
import com.remindme.domain.use_case.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Receives the exact-alarm broadcasts armed by [ReminderScheduler] (task 4.3).
 * Posts the advance alert, records the delivery in the ledger, and re-arms the
 * next occurrence — unless the task was completed meanwhile, in which case the
 * alarm chain stops. A post-time quiet-hours check (v2 review follow-up)
 * suppresses the notification and simply re-arms the next occurrence.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId <= 0) return

        val app = context.applicationContext as? RemindMeApp ?: return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = app.container
                val task = container.taskRepository.getById(taskId) ?: return@launch
                if (task.type != TaskType.FIXED || task.done) return@launch

                val now = System.currentTimeMillis()
                val config = container.antiSpamConfigProvider()
                if (QuietHours.isInsideQuietWindow(
                        TimeUtils.minuteOfDay(now, TimeZone.getDefault()),
                        config.quietStartMinute,
                        config.quietEndMinute,
                    )
                ) {
                    // Quiet hours: suppress the notification, keep the chain alive.
                    container.reminderScheduler.scheduleAdvanceAlert(task)
                    return@launch
                }

                if (container.reminderNotifier.postAdvanceAlert(task)) {
                    container.reminderRepository.recordDelivery(ReminderKind.ADVANCE_ALERT, taskId)
                }

                // Re-arm for the next occurrence unless the user completed the task
                // between the alarm firing and this receiver's check.
                val reloaded = container.taskRepository.getById(taskId) ?: return@launch
                if (!reloaded.done) {
                    container.reminderScheduler.scheduleAdvanceAlert(reloaded)
                }
            } finally {
                result.finish()
            }
        }
    }
}