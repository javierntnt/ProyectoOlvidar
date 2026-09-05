package com.remindme.ui.week

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import com.remindme.domain.model.TaskWriteResult
import com.remindme.domain.use_case.CreateTask
import com.remindme.domain.use_case.DeleteTask
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
 * WeekViewModel tests (task 5.1 + 5.4): week windowing, day/type grouping,
 * habit 0-done edge, alarm wiring for complete/delete.
 *
 * The VM observes the real in-memory Room flow, which emits on its own
 * executor threads, so assertions wait for state in REAL time instead of
 * pretending the virtual test clock can drive Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WeekViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var createTask: CreateTask
    private lateinit var updateTask: UpdateTask
    private lateinit var deleteTask: DeleteTask

    private val scheduled = CopyOnWriteArrayList<Task>()
    private val cancelled = CopyOnWriteArrayList<Long>()

    private val utc = TimeZone.getTimeZone("UTC")
    // Monday 2026-01-05 10:00 UTC.
    private val now = 1_767_607_200_000L
    private val mondayMidnight = 1_767_571_200_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        createTask = CreateTask(repository)
        updateTask = UpdateTask(repository)
        deleteTask = DeleteTask(repository)
        // Eager Main dispatcher: VM coroutines start immediately when subscribed.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel(): WeekViewModel = WeekViewModel(
        taskRepository = repository,
        updateTask = updateTask,
        deleteTask = deleteTask,
        scheduleAlarm = { task -> scheduled += task },
        cancelAlarm = { id -> cancelled += id },
        clock = { now },
        timeZone = utc,
    )

    /** Awaits a uiState emission matching [predicate]; real-time because Room emits on real threads. */
    private suspend fun awaitUiState(
        vm: WeekViewModel,
        predicate: (WeekUiState) -> Boolean,
    ): WeekUiState = withContext(Dispatchers.Default.limitedParallelism(1)) {
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
    fun `week defaults to the monday of the current week with today highlighted`() = runTest {
        val vm = viewModel()
        val state = awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight }

        assertEquals(mondayMidnight, state.weekStartEpochMillis)
        assertEquals(mondayMidnight, state.todayEpochMillis)
        assertTrue(state.days[0].isToday)
        assertFalse(state.days[1].isToday)
        assertEquals(7, state.days.size)
    }

    @Test
    fun `groups tasks by day and type and shows habit zero-progress edge`() = runTest {
        createTask(name = "Standup", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 540, nowEpochMillis = now)
        createTask(name = "Review", type = TaskType.FIXED, dayOfWeek = 3, timeMinute = 600, nowEpochMillis = now)
        createTask(name = "Read", type = TaskType.FLEXIBLE, nowEpochMillis = now)
        createTask(
            name = "Exercise",
            type = TaskType.HABIT,
            dayOfWeek = 4,
            habitFrequency = HabitFrequency.DAILY,
            habitTargetHours = 2,
            nowEpochMillis = now,
        )

        val vm = viewModel()
        val state = awaitUiState(vm) { it.anytimeTasks.size == 1 && it.days[0].fixedTasks.isNotEmpty() }

        assertEquals(listOf("Standup"), state.days[0].fixedTasks.map { it.name })
        assertEquals(listOf("Review"), state.days[2].fixedTasks.map { it.name })
        assertEquals(listOf("Read"), state.anytimeTasks.map { it.name })

        val habit = state.days[3].habitTasks.single()
        assertEquals(2, habit.habitTargetHours)
        assertEquals(0, habit.habitDoneHours) // spec edge: no progress → 0 done
        assertEquals("Exercise", habit.name)

        // FLEXIBLE must not also appear on a day.
        assertTrue(state.days.all { it.fixedTasks.none { t -> t.name == "Read" } })
    }

    @Test
    fun `completing a fixed task cancels its alarm and reopening re-arms it`() = runTest {
        val created = createTask(name = "Gym", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 540, nowEpochMillis = now)
        val task = (created as TaskWriteResult.Saved).task

        val vm = viewModel()
        awaitUiState(vm) { it.days.size == 7 && it.days[0].fixedTasks.any { t -> t.id == task.id } }

        vm.toggleDone(task)
        awaitCondition { cancelled.contains(task.id) }
        val doneState = awaitUiState(vm) {
            it.days.size == 7 && it.days[0].fixedTasks.any { t -> t.id == task.id && t.done }
        }
        val doneTask = doneState.days[0].fixedTasks.single { it.id == task.id }
        assertTrue(doneTask.done)

        vm.toggleDone(doneTask)
        awaitCondition { scheduled.any { it.id == task.id && !it.done } }
        val reopened = awaitUiState(vm) {
            it.days.size == 7 && it.days[0].fixedTasks.any { t -> t.id == task.id && !t.done }
        }.days[0].fixedTasks.single { it.id == task.id }
        assertFalse(reopened.done)
    }

    @Test
    fun `deleting a task cancels its scheduled advance alert`() = runTest {
        val created = createTask(name = "Dentist", type = TaskType.FIXED, dayOfWeek = 2, timeMinute = 600, nowEpochMillis = now)
        val task = (created as TaskWriteResult.Saved).task

        val vm = viewModel()
        awaitUiState(vm) { it.days.size == 7 && it.days[1].fixedTasks.any { t -> t.id == task.id } }

        vm.delete(task)
        awaitCondition { cancelled.contains(task.id) }

        val after = awaitUiState(vm) {
            it.days.size == 7 && it.days[1].fixedTasks.none { t -> t.id == task.id }
        }
        assertTrue(after.days[1].fixedTasks.none { it.id == task.id })
        assertNull(repository.getById(task.id))
    }

    @Test
    fun `week navigation shifts the window and today resets it`() = runTest {
        val vm = viewModel()
        awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight }

        vm.nextWeek()
        assertEquals(
            mondayMidnight + 7 * 86_400_000L,
            awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight + 7 * 86_400_000L }.weekStartEpochMillis,
        )
        assertTrue(awaitUiState(vm) { it.weekStartEpochMillis != mondayMidnight }.days.none { it.isToday })

        vm.previousWeek()
        awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight }

        vm.nextWeek()
        awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight + 7 * 86_400_000L }
        vm.goToCurrentWeek()
        val state = awaitUiState(vm) { it.weekStartEpochMillis == mondayMidnight && it.days[0].isToday }
        assertEquals(mondayMidnight, state.weekStartEpochMillis)
        assertTrue(state.days[0].isToday)
    }

    @Test
    fun `habit done hours survive completion toggle`() = runTest {
        createTask(
            name = "Practice",
            type = TaskType.HABIT,
            dayOfWeek = 5,
            habitFrequency = HabitFrequency.DAILY,
            habitTargetHours = 3,
            habitDoneHours = 1,
            nowEpochMillis = now,
        )

        val vm = viewModel()
        val habit = awaitUiState(vm) { it.days.size == 7 && it.days[4].habitTasks.any { t -> t.name == "Practice" } }
            .days[4].habitTasks.single { it.name == "Practice" }
        assertEquals(1, habit.habitDoneHours)

        vm.toggleDone(habit)
        val after = awaitUiState(vm) {
            it.days.size == 7 && it.days[4].habitTasks.any { t -> t.name == "Practice" && t.done }
        }.days[4].habitTasks.single { it.name == "Practice" }
        assertTrue(after.done)
        assertEquals(1, after.habitDoneHours)
        // No alarm wiring for habits.
        assertTrue(scheduled.isEmpty())
        assertTrue(cancelled.isEmpty())
    }
}