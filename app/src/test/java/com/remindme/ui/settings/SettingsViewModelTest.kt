package com.remindme.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.prefs.ReminderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * SettingsViewModel tests (task 5.3, spec User-Controlled Intervals): the state
 * mirrors ReminderPrefs and every change persists through DataStore.
 *
 * DataStore performs real I/O on its own threads, so the state is awaited in
 * REAL time (the stream emitter runs off the virtual test clock).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SettingsViewModelTest {

    private lateinit var prefs: ReminderPrefs
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        // Must replace Main BEFORE the ViewModel is constructed: viewModelScope
        // captures Dispatchers.Main.immediate at creation time, and Robolectric's
        // real looper is paused (queued launches would never run).
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = ReminderPrefs(ApplicationProvider.getApplicationContext<Application>())
        viewModel = SettingsViewModel(prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Awaits a uiState emission matching [predicate]; real-time because DataStore emits on real threads. */
    private suspend fun awaitUiState(predicate: (SettingsUiState) -> Boolean): SettingsUiState =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(10_000) { viewModel.uiState.first(predicate) }
        }

    @Test
    fun `initial state matches the ReminderPrefs defaults`() = runTest {
        val state = awaitUiState { it.leadTimeMinutes == ReminderPrefs.DEFAULT_LEAD_TIME_MINUTES }
        assertEquals(ReminderPrefs.DEFAULT_LEAD_TIME_MINUTES, state.leadTimeMinutes)
        assertEquals(ReminderPrefs.DEFAULT_REMINDER_INTERVAL_MIN, state.reminderIntervalMinutes)
        assertEquals(ReminderPrefs.DEFAULT_DAILY_CAP, state.dailyCap)
        assertEquals(ReminderPrefs.DEFAULT_COOLDOWN_MINUTES, state.cooldownMinutes)
        assertEquals(ReminderPrefs.DEFAULT_QUIET_START_MINUTE, state.quietStartMinute)
        assertEquals(ReminderPrefs.DEFAULT_QUIET_END_MINUTE, state.quietEndMinute)
    }

    @Test
    fun `changes persist through DataStore and reflect in state`() = runTest {
        viewModel.setLeadTimeMinutes(45)
        viewModel.setReminderIntervalMinutes(90)
        viewModel.setDailyCap(5)
        viewModel.setCooldownMinutes(30)
        viewModel.setQuietStartMinute(1320) // 22:00
        viewModel.setQuietEndMinute(360)    // 06:00

        val state = awaitUiState {
            it.leadTimeMinutes == 45 &&
                it.reminderIntervalMinutes == 90 &&
                it.dailyCap == 5 &&
                it.cooldownMinutes == 30 &&
                it.quietStartMinute == 1320 &&
                it.quietEndMinute == 360
        }
        assertEquals(45, state.leadTimeMinutes)
        assertEquals(90, state.reminderIntervalMinutes)
        assertEquals(5, state.dailyCap)
        assertEquals(30, state.cooldownMinutes)
        assertEquals(1320, state.quietStartMinute)
        assertEquals(360, state.quietEndMinute)

        // Round-trip through the repository layer (DataStore file).
        assertEquals(45, prefs.leadTimeMinutes.first())
        assertEquals(90, prefs.reminderIntervalMinutes.first())
    }

    @Test
    fun `out-of-range inputs are clamped before persistence`() = runTest {
        viewModel.setLeadTimeMinutes(5000)
        viewModel.setDailyCap(-3)
        viewModel.setCooldownMinutes(-10)

        val state = awaitUiState {
            it.leadTimeMinutes == 120 && it.dailyCap == 1 && it.cooldownMinutes == 0
        }
        assertEquals(120, state.leadTimeMinutes)
        assertEquals(1, state.dailyCap)
        assertEquals(0, state.cooldownMinutes)
    }

    @Test
    fun `interval floors at the workmanager minimum`() = runTest {
        viewModel.setReminderIntervalMinutes(1)
        val state = awaitUiState { it.reminderIntervalMinutes == 15 }
        assertEquals(15, state.reminderIntervalMinutes)
        // The persisted value must also be clamped, not the raw input.
        assertEquals(15, prefs.reminderIntervalMinutes.first())
    }
}