package com.remindme.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remindme.domain.model.ReminderKind
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReminderLogEntity): Long

    /** Count deliveries of a given [kind] since an epoch-millis timestamp (for daily-cap gate). */
    @Query("SELECT COUNT(*) FROM reminder_log WHERE kind = :kind AND at >= :since")
    suspend fun countSince(kind: ReminderKind, since: Long): Int

    /** Observe recent logs (for debugging / future settings display). */
    @Query("SELECT * FROM reminder_log WHERE kind = :kind AND at >= :since ORDER BY at DESC")
    fun logsSince(kind: ReminderKind, since: Long): Flow<List<ReminderLogEntity>>

    /** Prune old entries. Called by the periodic worker to prevent unbounded growth. */
    @Query("DELETE FROM reminder_log WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}