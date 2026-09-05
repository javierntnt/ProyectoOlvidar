package com.remindme.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room DAO smoke tests against an in-memory database.
 *
 * Robolectric provides an Android [Application] context on the JVM.
 * We target SDK 33 because Robolectric 4.14 supports API 33–35.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RoomDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() { db.close() }

    // ---- TaskDao -------------------------------------------------------------

    @Test
    fun `upsert and retrieve by id`() = runTest {
        val entity = TaskEntity(
            name = "Standup",
            type = TaskType.FIXED,
            dayOfWeek = 1,
            timeMinute = 540,
            createdAt = System.currentTimeMillis(),
        )
        val id = db.taskDao().upsert(entity)
        val fetched = db.taskDao().getById(id)

        assertNotNull(fetched)
        assertEquals("Standup", fetched!!.name)
        assertEquals(TaskType.FIXED, fetched.type)
    }

    @Test
    fun `observeAll returns tasks ordered`() = runTest {
        val now = System.currentTimeMillis()
        db.taskDao().upsert(TaskEntity(name = "B", type = TaskType.FLEXIBLE, createdAt = now))
        db.taskDao().upsert(TaskEntity(name = "A", type = TaskType.FLEXIBLE, createdAt = now))

        val all = db.taskDao().observeAll().first()
        assertEquals(2, all.size)
        // Ordered by name ASC for same dayOfWeek (both null)
        assertEquals("A", all[0].name)
        assertEquals("B", all[1].name)
    }

    @Test
    fun `tasksForDay filters correctly`() = runTest {
        val now = System.currentTimeMillis()
        db.taskDao().upsert(TaskEntity(name = "Mon task", type = TaskType.FIXED, dayOfWeek = 1, createdAt = now))
        db.taskDao().upsert(TaskEntity(name = "Tue task", type = TaskType.FIXED, dayOfWeek = 2, createdAt = now))

        val monday = db.taskDao().tasksForDay(1).first()
        assertEquals(1, monday.size)
        assertEquals("Mon task", monday[0].name)
    }

    @Test
    fun `deleteById removes the task`() = runTest {
        val id = db.taskDao().upsert(
            TaskEntity(name = "Temp", type = TaskType.FLEXIBLE, createdAt = System.currentTimeMillis())
        )
        db.taskDao().deleteById(id)
        assertNull(db.taskDao().getById(id))
    }

    @Test
    fun `getAllPending returns only undone tasks`() = runTest {
        val now = System.currentTimeMillis()
        db.taskDao().upsert(TaskEntity(name = "Pending", type = TaskType.FLEXIBLE, done = false, createdAt = now))
        db.taskDao().upsert(TaskEntity(name = "Done", type = TaskType.FLEXIBLE, done = true, createdAt = now))

        val pending = db.taskDao().getAllPending()
        assertEquals(1, pending.size)
        assertEquals("Pending", pending[0].name)
    }

    @Test
    fun `habit entity preserves all type-specific fields`() = runTest {
        val entity = TaskEntity(
            name = "Exercise",
            type = TaskType.HABIT,
            dayOfWeek = 5,
            habitFrequency = HabitFrequency.DAILY,
            habitTargetHours = 1,
            habitDoneHours = 0,
            createdAt = System.currentTimeMillis(),
        )
        val id = db.taskDao().upsert(entity)
        val fetched = db.taskDao().getById(id)!!

        assertEquals(HabitFrequency.DAILY, fetched.habitFrequency)
        assertEquals(1, fetched.habitTargetHours)
        assertEquals(0, fetched.habitDoneHours)
    }

    // ---- ReminderLogDao -----------------------------------------------------

    @Test
    fun `insert and countSince returns correct count`() = runTest {
        val now = System.currentTimeMillis()
        db.reminderLogDao().insert(ReminderLogEntity(kind = ReminderKind.ADVANCE_ALERT, taskId = 1, at = now))
        db.reminderLogDao().insert(ReminderLogEntity(kind = ReminderKind.PENDING_REMINDER, taskId = null, at = now))

        val count = db.reminderLogDao().countSince(ReminderKind.ADVANCE_ALERT, now - 1000)
        assertEquals(1, count) // only ADVANCE_ALERT counted

        val countAll = db.reminderLogDao().countSince(ReminderKind.PENDING_REMINDER, now - 1000)
        assertEquals(1, countAll)
    }

    @Test
    fun `countSince returns zero for future timestamp`() = runTest {
        db.reminderLogDao().insert(
            ReminderLogEntity(kind = ReminderKind.ADVANCE_ALERT, taskId = 1, at = System.currentTimeMillis())
        )
        val count = db.reminderLogDao().countSince(ReminderKind.ADVANCE_ALERT, System.currentTimeMillis() + 100_000)
        assertEquals(0, count) // nothing in the future
    }

    @Test
    fun `deleteOlderThan prunes old entries`() = runTest {
        val now = System.currentTimeMillis()
        db.reminderLogDao().insert(ReminderLogEntity(kind = ReminderKind.ADVANCE_ALERT, taskId = 1, at = now))
        db.reminderLogDao().insert(ReminderLogEntity(kind = ReminderKind.PENDING_REMINDER, taskId = null, at = now - 100_000))

        db.reminderLogDao().deleteOlderThan(now - 50_000)

        val remaining = db.reminderLogDao().countSince(ReminderKind.ADVANCE_ALERT, 0)
        val remainingPending = db.reminderLogDao().countSince(ReminderKind.PENDING_REMINDER, 0)
        assertEquals(1, remaining)       // recent one kept
        assertEquals(0, remainingPending) // old one pruned
    }

    // ---- Entity ↔ Domain mapping --------------------------------------------

    @Test
    fun `TaskEntity toDomain and fromDomain round-trip`() = runTest {
        val original = TaskEntity(
            id = 42,
            name = "Deep Work",
            type = TaskType.FIXED,
            dayOfWeek = 3,
            timeMinute = 480,
            habitFrequency = null,
            habitTargetHours = null,
            habitDoneHours = 0,
            done = false,
            createdAt = 1_700_000_000_000L,
        )
        val domain = original.toDomain()
        assertEquals(42L, domain.id)
        assertEquals("Deep Work", domain.name)

        val roundTripped = TaskEntity.fromDomain(domain)
        assertEquals(original.copy(id = 0).copy(id = 0), roundTripped.copy(id = 0))
    }
}