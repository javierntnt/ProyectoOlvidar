package com.remindme.domain.use_case

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * CreateTask use-case tests (spec: Create Task + Three Task Types).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CreateTaskUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var useCase: CreateTask

    private val now = 1_767_607_200_000L // 2026-01-05T10:00Z

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        useCase = CreateTask(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `creates a valid fixed task and returns the saved id`() = runTest {
        val result = useCase(
            name = "Standup",
            type = TaskType.FIXED,
            dayOfWeek = 1,
            timeMinute = 540,
            nowEpochMillis = now,
        )

        assertTrue(result is TaskWriteResult.Saved)
        val saved = (result as TaskWriteResult.Saved).task
        assertTrue(saved.id > 0)
        assertEquals("Standup", saved.name)
        assertEquals(now, saved.createdAt)

        // Persisted: observable through the repository.
        assertEquals("Standup", repository.getById(saved.id)?.name)
    }

    @Test
    fun `trims the task name before saving`() = runTest {
        val result = useCase(name = "  Read  ", type = TaskType.FLEXIBLE, nowEpochMillis = now)
        assertEquals("Read", (result as TaskWriteResult.Saved).task.name)
    }

    @Test
    fun `rejects an empty name without persisting anything`() = runTest {
        val result = useCase(name = "   ", type = TaskType.FLEXIBLE, nowEpochMillis = now)
        assertEquals(TaskInputError.EMPTY_NAME, (result as TaskWriteResult.Invalid).error)
        assertEquals(0, repository.observeAll().first().size)
    }

    @Test
    fun `rejects invalid fixed-task fields`() = runTest {
        val invalidDay = useCase(name = "X", type = TaskType.FIXED, dayOfWeek = 9, timeMinute = 540, nowEpochMillis = now)
        assertEquals(TaskInputError.INVALID_DAY, (invalidDay as TaskWriteResult.Invalid).error)

        val invalidTime = useCase(name = "X", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 2000, nowEpochMillis = now)
        assertEquals(TaskInputError.INVALID_TIME, (invalidTime as TaskWriteResult.Invalid).error)

        assertEquals(0, repository.observeAll().first().size)
    }

    @Test
    fun `creates a habit task with frequency and target hours`() = runTest {
        val result = useCase(
            name = "Exercise",
            type = TaskType.HABIT,
            dayOfWeek = 3,
            habitFrequency = HabitFrequency.DAILY,
            habitTargetHours = 1,
            nowEpochMillis = now,
        )

        assertTrue(result is TaskWriteResult.Saved)
        val saved = (result as TaskWriteResult.Saved).task
        assertEquals(HabitFrequency.DAILY, saved.habitFrequency)
        assertEquals(1, saved.habitTargetHours)
        assertEquals(0, saved.habitDoneHours) // edge case: no progress yet → 0 done
    }

    @Test
    fun `rejects a habit task without a positive target`() = runTest {
        val result = useCase(
            name = "Exercise",
            type = TaskType.HABIT,
            habitFrequency = HabitFrequency.WEEKLY,
            habitTargetHours = 0,
            nowEpochMillis = now,
        )
        assertEquals(TaskInputError.INVALID_HABIT_TARGET, (result as TaskWriteResult.Invalid).error)
    }
}