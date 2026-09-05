package com.remindme.ui.week

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.domain.model.HabitFrequency
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskInputError
import com.remindme.domain.model.TaskType
import com.remindme.ui.task.TaskFormContent
import com.remindme.ui.task.TaskFormUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke tests (spec E2E seed, task 6.4 — device/emulator only).
 * These render the stateless screen content with fixed state; they cannot run
 * in the local JVM toolchain, so they are exercised via connectedAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class WeekViewContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleState = WeekUiState(
        weekStartEpochMillis = 0L,
        todayEpochMillis = 0L,
        weekStartLabel = "Sep 7",
        weekEndLabel = "Sep 13",
        anytimeTasks = listOf(
            Task(id = 3, name = "Read book", type = TaskType.FLEXIBLE, createdAt = 0L),
        ),
        days = listOf(
            DayUi(
                dayOfWeek = 1,
                dateEpochMillis = 0L,
                isToday = true,
                fixedTasks = listOf(
                    Task(id = 1, name = "Standup", type = TaskType.FIXED, dayOfWeek = 1, timeMinute = 540, createdAt = 0L),
                ),
                habitTasks = listOf(
                    Task(
                        id = 2,
                        name = "Exercise",
                        type = TaskType.HABIT,
                        dayOfWeek = 1,
                        habitFrequency = HabitFrequency.DAILY,
                        habitTargetHours = 2,
                        habitDoneHours = 1,
                        createdAt = 0L,
                    ),
                ),
            ),
            DayUi(dayOfWeek = 2, dateEpochMillis = 0L, isToday = false),
        ),
    )

    @Test
    fun weekView_rendersTasksGroupedByDayAndType() {
        composeTestRule.setContent {
            WeekViewContent(
                state = sampleState,
                onToggleDone = {},
                onDelete = {},
                onEdit = {},
                onAdd = {},
                onPreviousWeek = {},
                onNextWeek = {},
                onToday = {},
            )
        }

        composeTestRule.onNodeWithText("Standup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exercise").assertIsDisplayed()
        composeTestRule.onNodeWithText("1/2 h").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read book").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun taskForm_showsInlineErrorForEmptyName() {
        composeTestRule.setContent {
            TaskFormContent(
                state = TaskFormUiState(
                    name = "",
                    type = TaskType.FIXED,
                    dayOfWeek = 1,
                    timeMinute = 540,
                    error = TaskInputError.EMPTY_NAME,
                    loaded = true,
                ),
                onNameChange = {},
                onTypeChange = {},
                onDayChange = {},
                onTimeChange = {},
                onFrequencyChange = {},
                onTargetChange = {},
                onDoneHoursChange = {},
                onSave = {},
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("Task name is required").assertIsDisplayed()
    }

    @Test
    fun taskForm_showsHabitStepperForHabitType() {
        composeTestRule.setContent {
            TaskFormContent(
                state = TaskFormUiState(
                    name = "Exercise",
                    type = TaskType.HABIT,
                    dayOfWeek = 1,
                    habitFrequency = HabitFrequency.DAILY,
                    habitTargetHours = 2,
                    habitDoneHours = 1,
                    loaded = true,
                ),
                onNameChange = {},
                onTypeChange = {},
                onDayChange = {},
                onTimeChange = {},
                onFrequencyChange = {},
                onTargetChange = {},
                onDoneHoursChange = {},
                onSave = {},
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("Target hours").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hours done").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weekly").assertIsDisplayed()
    }
}