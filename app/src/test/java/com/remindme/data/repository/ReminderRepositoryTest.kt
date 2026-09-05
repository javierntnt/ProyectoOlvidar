package com.remindme.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.domain.model.ReminderKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Ledger (ReminderLog) repository tests — task 2.4 + data-persistence spec
 * requirement: "delivered state is restored after restart".
 *
 * [ledger survives database close and reopen] re-creates the database from the
 * same on-disk file, which is exactly what happens when the app process restarts:
 * the Room file is reopened and prior delivery records still count against
 * the anti-spam daily cap.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ReminderRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ReminderRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReminderRepository(db.reminderLogDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `recordDelivery persists a ledger entry`() = runTest {
        val id = repository.recordDelivery(ReminderKind.ADVANCE_ALERT, taskId = 7)

        assertEquals(1, repository.countDeliveredSince(ReminderKind.ADVANCE_ALERT, 0))
        assertEquals(1L, id)
    }

    @Test
    fun `countDeliveredSince only counts matching kind since timestamp`() = runTest {
        val now = System.currentTimeMillis()
        repository.recordDelivery(ReminderKind.ADVANCE_ALERT, taskId = 1)
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, taskId = null)

        assertEquals(1, repository.countDeliveredSince(ReminderKind.ADVANCE_ALERT, now - 10_000))
        assertEquals(1, repository.countDeliveredSince(ReminderKind.PENDING_REMINDER, now - 10_000))
        // Future timestamp: nothing counted (no notification "delivered" after now).
        assertEquals(0, repository.countDeliveredSince(ReminderKind.ADVANCE_ALERT, now + 10_000))
    }

    @Test
    fun `ledger survives database close and reopen (restart semantics)`() = runTest {
        val now = System.currentTimeMillis()
        val dbName = "ledger_restart_${System.nanoTime()}.db"
        try {
            // "First process run": write two deliveries, close the database.
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
                .also { first ->
                    val repo = ReminderRepository(first.reminderLogDao())
                    repo.recordDelivery(ReminderKind.PENDING_REMINDER, taskId = null)
                    repo.recordDelivery(ReminderKind.PENDING_REMINDER, taskId = null)
                    first.close()
                }

            // "App restart": reopen the same file — the cap must still count 2/3.
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            try {
                val repo = ReminderRepository(reopened.reminderLogDao())
                assertEquals(2, repo.countDeliveredSince(ReminderKind.PENDING_REMINDER, now - 60_000))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `prune removes only old ledger entries`() = runTest {
        val now = System.currentTimeMillis()
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, taskId = null) // now
        repository.prune(olderThanEpochMillis = now - 10)
        assertEquals(1, repository.countDeliveredSince(ReminderKind.PENDING_REMINDER, 0))
    }
}