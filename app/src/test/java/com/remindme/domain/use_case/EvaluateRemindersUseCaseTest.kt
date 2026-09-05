package com.remindme.domain.use_case

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.ReminderRepository
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.AntiSpamConfig
import com.remindme.domain.model.BlockReason
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * EvaluateReminders use-case tests (spec: Periodic Pending Reminders + the
 * anti-spam policy). Only flexible/habit tasks that are not done are candidates.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class EvaluateRemindersUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var taskRepository: TaskRepository
    private lateinit var reminderRepository: ReminderRepository

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_767_607_200_000L // Monday 2026-01-05T10:00Z
    private val dayStart = 1_767_571_200_000L

    private fun evaluate(cap: Int = 3, quietStart: Int = 1380, quietEnd: Int = 420): EvaluateReminders {
        val gate = AntiSpamGate(reminderRepository) {
            AntiSpamConfig(cap, 240, quietStart, quietEnd)
        }
        return EvaluateReminders(taskRepository, gate, clock = { now })
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskRepository = TaskRepository(db.taskDao())
        reminderRepository = ReminderRepository(db.reminderLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `returns pending flexible and habit tasks when the gate allows`() = runTest {
        taskRepository.save(Task(name = "Fixed call", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 540, createdAt = now))
        taskRepository.save(Task(name = "Read", type = TaskType.FLEXIBLE, createdAt = now))
        taskRepository.save(Task(name = "Exercise", type = TaskType.HABIT, dayOfWeek = 3, habitFrequency = HabitFrequency.DAILY, habitTargetHours = 1, createdAt = now))
        taskRepository.save(Task(name = "Done flex", type = TaskType.FLEXIBLE, done = true, createdAt = now))

        val result = evaluate().evaluate(utc)

        assertTrue(result.allowed)
        assertNull(result.blockedReason)
        // FIXED tasks keep their own alarms; done tasks are never nudged.
        assertEquals(listOf("Exercise", "Read"), result.pendingTasks.map { it.name }.sorted())
    }

    @Test
    fun `returns no tasks when the daily cap is reached`() = runTest {
        taskRepository.save(Task(name = "Read", type = TaskType.FLEXIBLE, createdAt = now))
        repeat(3) { i ->
            reminderRepository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + (i + 1) * 3_600_000L)
        }

        val result = evaluate().evaluate(utc)

        assertFalse(result.allowed)
        assertEquals(BlockReason.DAILY_CAP_REACHED, result.blockedReason)
        assertTrue(result.pendingTasks.isEmpty())
    }

    @Test
    fun `returns no tasks during quiet hours`() = runTest {
        taskRepository.save(Task(name = "Read", type = TaskType.FLEXIBLE, createdAt = now))

        val atOneAm = dayStart + 3_600L
        val result = evaluate(quietStart = 1380, quietEnd = 420).evaluate(utc, atOneAm)

        assertFalse(result.allowed)
        assertEquals(BlockReason.QUIET_HOURS, result.blockedReason)
        assertTrue(result.pendingTasks.isEmpty())
    }
}