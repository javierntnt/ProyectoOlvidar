package com.remindme.ui.task

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import com.remindme.domain.use_case.CreateTask
import com.remindme.domain.use_case.UpdateTask
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * TaskFormViewModel tests (task 5.2): type-specific fields, inline empty-name
 * rejection (spec Create Task "REJECTED: empty name"), and alarm wiring for
 * create/edit of FIXED tasks (integration with ReminderScheduler).
 *
 * Room runs on its own executor threads, so state changes that depend on a
 * database round-trip are awaited in REAL time before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TaskFormViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var createTask: CreateTask
    private lateinit var updateTask: UpdateTask

    private val scheduled = CopyOnWriteArrayList<Task>()
    private val cancelled = CopyOnWriteArrayList<Long>()

    private val utc = TimeZone.getTimeZone("UTC")
    // Monday 2026-01-05 10:00 UTC.
    private val now = 1_767_607_200_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        createTask = CreateTask(repository)
        updateTask = UpdateTask(repository)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel(taskId: Long? = null): TaskFormViewModel = TaskFormViewModel(
        createTask = createTask,
        updateTask = updateTask,
        taskRepository = repository,
        scheduleAlarm = { task -> scheduled += task },
        cancelAlarm = { id -> cancelled += id },
        taskId = taskId,
        clock = { now },
        timeZone = utc,
    )

    /** Awaits a uiState emission matching [predicate]; real-time because Room emits on real threads. */
    private suspend fun awaitUiState(
        vm: TaskFormViewModel,
        predicate: (TaskFormUiState) -> Boolean,
    ): TaskFormUiState = withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(10_000) { vm.uiState.first(predicate) }
    }

    /** Polls [condition] until true; real-time (same reason as [awaitUiState]). */
    private suspend fun awaitCondition(condition: () -> Boolean) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(10_000) {
                while (!condition()) delay(10)
            }
        }
    }

    @Test
    fun `new task defaults to today and 9am with fixed type`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals(TaskType.FIXED, state.type)
        assertEquals(1, state.dayOfWeek) // Monday
        assertEquals(540, state.timeMinute)
    }

    @Test
    fun `empty name surfaces the validation error and saves nothing`() = runTest {
        val vm = viewModel()

        vm.save()
        val state = awaitUiState(vm) { it.error == TaskInputError.EMPTY_NAME }

        assertEquals(TaskInputError.EMPTY_NAME, state.error)
        assertFalse(state.saved)
        assertTrue(scheduled.isEmpty())
        assertEquals(0, repository.observeAll().first().size)
    }

    @Test
    fun `creating a fixed task schedules its advance alert`() = runTest {
        val vm = viewModel()

        vm.onNameChange("Standup")
        vm.onDayChange(3)
        vm.onTimeChange(600)
        vm.save()
        awaitUiState(vm) { it.saved }

        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.saved)
        awaitCondition { cancelled.size == 1 } // stale-alarm cancel for the fresh id (no-op alarm)
        awaitCondition { scheduled.size == 1 }
        val saved = scheduled.single()
        assertEquals("Standup", saved.name)
        assertEquals(3, saved.dayOfWeek)
        assertEquals(600, saved.timeMinute)
        assertTrue(saved.id > 0)
    }

    @Test
    fun `creating a habit persists stepper hours without scheduling alarms`() = runTest {
        val vm = viewModel()

        vm.onNameChange("Exercise")
        vm.onTypeChange(TaskType.HABIT)
        vm.onFrequencyChange(HabitFrequency.DAILY)
        vm.onTargetChange(2)
        vm.onDoneHoursChange(1)
        vm.save()
        awaitUiState(vm) { it.saved }

        val persisted = repository.observeAll().first().single()
        assertEquals(TaskType.HABIT, persisted.type)
        assertEquals(HabitFrequency.DAILY, persisted.habitFrequency)
        assertEquals(2, persisted.habitTargetHours)
        assertEquals(1, persisted.habitDoneHours)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `switching type away from fixed clears the time`() = runTest {
        val vm = viewModel()

        vm.onTypeChange(TaskType.FLEXIBLE)
        assertNull(vm.uiState.value.timeMinute)
        assertNull(vm.uiState.value.dayOfWeek)
    }

    @Test
    fun `edit mode loads the task and save reschedules the alarm`() = runTest {
        val created = createTask(
            name = "Gym",
            type = TaskType.FIXED,
            dayOfWeek = 2,
            timeMinute = 540,
            nowEpochMillis = now,
        )
        val task = (created as TaskWriteResult.Saved).task

        val vm = viewModel(taskId = task.id)
        val loaded = awaitUiState(vm) { it.loaded && it.name == "Gym" }
        assertTrue(loaded.loaded)
        assertEquals(TaskType.FIXED, loaded.type)
        assertEquals(2, loaded.dayOfWeek)
        assertEquals(540, loaded.timeMinute)

        vm.onTimeChange(600)
        vm.save()
        awaitUiState(vm) { it.saved }

        awaitCondition { cancelled.contains(task.id) }
        awaitCondition { scheduled.size == 1 }
        assertEquals(600, scheduled.single().timeMinute)
        assertEquals(task.id, scheduled.single().id)
    }

    @Test
    fun `editing a task that does not exist surfaces not-found`() = runTest {
        val vm = viewModel(taskId = 999L)
        val state = awaitUiState(vm) { it.error == TaskInputError.TASK_NOT_FOUND }

        assertEquals(TaskInputError.TASK_NOT_FOUND, state.error)
        vm.save() // guard: save is a no-op when not-found is surfaced
        assertFalse(vm.uiState.value.saved)
        assertTrue(scheduled.isEmpty())
    }
}