package com.remindme.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Repository-level persistence tests against a real Room database.
 *
 * These prove task CRUD survives the entity <-> domain mapping (task 2.4:
 * "in-memory Room tests proving persistence"). Offline is guaranteed by
 * construction: repositories only talk to Room — no network code exists
 * anywhere in the data layer (manifest has no INTERNET permission either).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `save persists a task and getById restores it as domain model`() = runTest {
        val task = Task(
            name = "Standup",
            type = TaskType.FIXED,
            dayOfWeek = 1,
            timeMinute = 540,
            createdAt = 1_700_000_000_000L,
        )
        val id = repository.save(task)
        val restored = repository.getById(id)

        assertNotNull(restored)
        assertEquals("Standup", restored!!.name)
        assertEquals(TaskType.FIXED, restored.type)
        assertEquals(1, restored.dayOfWeek)
        assertEquals(540, restored.timeMinute)
        assertEquals(1_700_000_000_000L, restored.createdAt)
    }

    @Test
    fun `tasksForDay maps DAO rows to domain and filters by day`() = runTest {
        val now = 1_700_000_000_000L
        repository.save(Task(name = "Mon", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 480, createdAt = now))
        repository.save(Task(name = "Tue", type = TaskType.FIXED, dayOfWeek = 2, timeMinute = 480, createdAt = now))

        val monday = repository.tasksForDay(1).first()
        assertEquals(1, monday.size)
        assertEquals("Mon", monday[0].name)
    }

    @Test
    fun `update overwrites existing task fields`() = runTest {
        val id = repository.save(
            Task(name = "Old name", type = TaskType.FLEXIBLE, createdAt = 1_700_000_000_000L)
        )
        repository.save(
            Task(id = id, name = "New name", type = TaskType.FLEXIBLE, createdAt = 1_700_000_000_000L)
        )

        val updated = repository.getById(id)
        assertEquals("New name", updated!!.name)
    }

    @Test
    fun `delete removes the task and pending queries no longer see it`() = runTest {
        val id = repository.save(
            Task(name = "Temp", type = TaskType.FLEXIBLE, createdAt = 1_700_000_000_000L)
        )
        repository.delete(id)

        assertNull(repository.getById(id))
        assertEquals(0, repository.getAllPending().size)
    }

    @Test
    fun `getAllPending returns only undone tasks`() = runTest {
        val now = 1_700_000_000_000L
        repository.save(Task(name = "Pending", type = TaskType.FLEXIBLE, createdAt = now))
        repository.save(Task(name = "Done", type = TaskType.FLEXIBLE, done = true, createdAt = now))

        val pending = repository.getAllPending()
        assertEquals(1, pending.size)
        assertEquals("Pending", pending[0].name)
    }
}