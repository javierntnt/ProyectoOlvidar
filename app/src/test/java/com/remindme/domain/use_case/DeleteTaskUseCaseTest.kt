package com.remindme.domain.use_case

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DeleteTask use-case tests (spec: Delete Task). Cancellation of scheduled
 * notifications is wired by the caller (Phase 5, task 5.4) via
 * [com.remindme.notifications.ReminderScheduler.cancelAdvanceAlert].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DeleteTaskUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var useCase: DeleteTask

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        useCase = DeleteTask(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `deletes an existing task`() = runTest {
        val id = repository.save(
            Task(name = "To delete", type = TaskType.FLEXIBLE, createdAt = System.currentTimeMillis()),
        )
        useCase(id)
        assertNull(repository.getById(id))
    }

    @Test
    fun `deleting a missing task is a no-op`() = runTest {
        useCase(42_242)
        assertEquals(0, repository.observeAll().first().size)
    }
}