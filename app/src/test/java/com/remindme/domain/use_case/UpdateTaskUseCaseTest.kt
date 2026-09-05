package com.remindme.domain.use_case

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * UpdateTask use-case tests (spec: Edit Task).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class UpdateTaskUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var useCase: UpdateTask

    private val now = 1_767_607_200_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        useCase = UpdateTask(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedTask(): Task {
        val id = repository.save(
            Task(name = "Old name", type = TaskType.FLEXIBLE, createdAt = now),
        )
        return repository.getById(id)!!
    }

    @Test
    fun `updates fields and persists them`() = runTest {
        val existing = seedTask()
        val result = useCase(
            existing.copy(name = "New name", type = TaskType.FIXED, dayOfWeek = 2, timeMinute = 600),
        )

        assertTrue(result is TaskWriteResult.Saved)
        val saved = (result as TaskWriteResult.Saved).task
        assertEquals("New name", saved.name)
        assertEquals(TaskType.FIXED, saved.type)

        val reloaded = repository.getById(saved.id)!!
        assertEquals("New name", reloaded.name)
        assertEquals(2, reloaded.dayOfWeek)
    }

    @Test
    fun `preserves createdAt across updates`() = runTest {
        val existing = seedTask()
        val result = useCase(existing.copy(name = "Renamed"))

        assertEquals(now, (result as TaskWriteResult.Saved).task.createdAt)
        assertEquals(now, repository.getById(existing.id)!!.createdAt)
    }

    @Test
    fun `rejects an empty name on update`() = runTest {
        val existing = seedTask()
        val result = useCase(existing.copy(name = "  "))

        assertEquals(TaskInputError.EMPTY_NAME, (result as TaskWriteResult.Invalid).error)
        assertEquals("Old name", repository.getById(existing.id)!!.name) // unchanged
    }

    @Test
    fun `rejects updates for a task that does not exist`() = runTest {
        val ghost = Task(id = 999_999, name = "Ghost", type = TaskType.FLEXIBLE, createdAt = now)
        val result = useCase(ghost)

        assertEquals(TaskInputError.TASK_NOT_FOUND, (result as TaskWriteResult.Invalid).error)
        assertNull(repository.getById(ghost.id)) // nothing inserted
    }
}