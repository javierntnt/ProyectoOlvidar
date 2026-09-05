package com.remindme.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.remindme.domain.model.ReminderKind

/**
 * Ledger of every notification the app actually delivered (or would deliver).
 * Persisted in Room so anti-spam caps survive app restarts, matching the spec's
 * Notification State requirement.
 */
@Entity(tableName = "reminder_log")
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Which kind of notification was delivered. */
    val kind: ReminderKind,
    /** The task that triggered this notification (may be null for periodic pending reminders). */
    val taskId: Long?,
    /** Epoch milliseconds when the notification was delivered / posted. */
    val at: Long,
)