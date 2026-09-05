package com.remindme.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.remindme.MainActivity
import com.remindme.R
import com.remindme.domain.model.Task

/**
 * Posts the two notification kinds (task 4.1). Returns false when the runtime
 * notification permission is missing or there is nothing to post, so callers
 * can skip recording a delivery in the anti-spam ledger.
 */
class ReminderNotifier(private val context: Context) {

    fun postAdvanceAlert(task: Task): Boolean {
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) return false

        val notification = NotificationCompat.Builder(context, NotificationChannels.ADVANCE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_advance_alert_title))
            .setContentText(context.getString(R.string.notification_advance_alert_text, task.name))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(ADVANCE_ALERT_BASE + task.id.toInt(), notification)
        return true
    }

    fun postPendingReminders(pendingTasks: List<Task>): Boolean {
        if (pendingTasks.isEmpty()) return false
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) return false

        val names = pendingTasks.take(MAX_PREVIEW_NAMES).joinToString(", ") { it.name }
        val text = context.getString(
            R.string.notification_pending_reminders_text,
            pendingTasks.size,
            names,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.PENDING_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_pending_reminders_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(PENDING_REMINDERS_ID, notification)
        return true
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val PENDING_REMINDERS_ID = 9999
        private const val ADVANCE_ALERT_BASE = 10_000
        private const val MAX_PREVIEW_NAMES = 3
    }
}