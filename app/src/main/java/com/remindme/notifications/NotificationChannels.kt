package com.remindme.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.remindme.R

/**
 * Two channels per the design (task 4.1): advance alerts use the high
 * importance (they accompany the user's fixed times); periodic pending
 * reminders use the default importance so nudges never feel urgent.
 */
object NotificationChannels {

    const val ADVANCE_ALERTS = "advance_alerts"
    const val PENDING_REMINDERS = "pending_reminders"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ADVANCE_ALERTS,
                context.getString(R.string.channel_advance_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_advance_alerts_description)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PENDING_REMINDERS,
                context.getString(R.string.channel_pending_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_pending_reminders_description)
            },
        )
    }
}