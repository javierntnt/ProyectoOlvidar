package com.remindme.data.repository

import com.remindme.data.local.ReminderLogDao
import com.remindme.data.local.ReminderLogEntity
import com.remindme.domain.model.ReminderKind
import kotlinx.coroutines.flow.Flow

/**
 * Facade over the reminder log ledger.
 * Used by the notification scheduler and the anti-spam gate
 * to persist delivery state across restarts.
 */
class ReminderRepository(private val dao: ReminderLogDao) {

    /** Record that a notification was delivered (stamped with [at], default now). */
    suspend fun recordDelivery(kind: ReminderKind, taskId: Long?, at: Long = System.currentTimeMillis()): Long =
        dao.insert(ReminderLogEntity(kind = kind, taskId = taskId, at = at))

    /** How many notifications of [kind] were delivered since [sinceEpochMillis] (daily-cap gate). */
    suspend fun countDeliveredSince(kind: ReminderKind, sinceEpochMillis: Long): Int =
        dao.countSince(kind, sinceEpochMillis)

    /** Epoch millis of the most recent delivery of [kind], or null (cooldown gate). */
    suspend fun latestDeliveryAt(kind: ReminderKind): Long? =
        dao.latestAt(kind)

    /** Observe recent logs (for future settings screen / debug). */
    fun observeLogsSince(kind: ReminderKind, sinceEpochMillis: Long): Flow<List<ReminderLogEntity>> =
        dao.logsSince(kind, sinceEpochMillis)

    /** Remove old entries (pruning, called periodically by the worker). */
    suspend fun prune(olderThanEpochMillis: Long) =
        dao.deleteOlderThan(olderThanEpochMillis)
}