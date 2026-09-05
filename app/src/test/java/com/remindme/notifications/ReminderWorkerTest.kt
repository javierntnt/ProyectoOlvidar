package com.remindme.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.remindme.AppContainer
import com.remindme.RemindMeApp
import com.remindme.TestRemindMeApp
import com.remindme.data.local.AppDatabase
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import com.remindme.domain.time.TimeUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Integration test for the periodic ReminderWorker (spec: Periodic Pending
 * Reminders + Anti-Spam Daily Cap + Notification State), driven through the real
 * WorkManager TestDriver — task 6.3.
 *
 * The app's `preferencesDataStore` is file-backed under Robolectric and fresh per
 * test method; [AppDatabase.resetInstanceForTest] guarantees a clean Room file too,
 * because the process-wide singleton would otherwise leak across test methods.
 *
 * Seed times are chosen so assertions are independent of the wall clock:
 * - "today" deliveries are seeded at dayStart + N minutes (always counted by the
 *   daily-cap query, even if a few of them lie in the future when the suite runs
 *   shortly after midnight);
 * - the "posts until cap" test overrides prefs so cooldown is 0, interval is 15
 *   minutes and the quiet window is empty; its deliveries are seeded 19–20 minutes
 *   in the past so the cooldown always allows the first run and blocks the second.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = TestRemindMeApp::class)
class ReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var app: RemindMeApp
    private lateinit var testDriver: TestDriver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        app = context as RemindMeApp
        AppDatabase.resetInstanceForTest()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `posts nothing when the daily cap is already reached`() = runBlocking {
        val container = app.container
        seedTask(container)

        // Three deliveries already recorded "today" → cap default 3 is exhausted.
        val today = TimeUtils.startOfDay(System.currentTimeMillis(), TimeZone.getDefault())
        repeat(3) { i ->
            container.reminderRepository.recordDelivery(
                ReminderKind.PENDING_REMINDER,
                taskId = null,
                at = today + (i + 1) * 60_000L,
            )
        }

        runPeriodicWorkerOnce()

        assertEquals(3, container.reminderRepository.countDeliveredSince(ReminderKind.PENDING_REMINDER, 0))
        val nm = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    @Test
    fun `posts up to the daily cap then stops`() = runBlocking {
        val container = app.container
        // Neutralize cooldown/quiet so the outcome depends only on the cap.
        container.reminderPrefs.setCooldownMinutes(0)
        container.reminderPrefs.setReminderIntervalMinutes(15)
        container.reminderPrefs.setQuietStartMinute(0)
        container.reminderPrefs.setQuietEndMinute(0)
        seedTask(container)

        // Two deliveries 19–20 minutes ago: inside "today" whenever the test runs,
        // and comfortably past the 15-minute minimum gap.
        val nowMs = System.currentTimeMillis()
        repeat(2) { i ->
            container.reminderRepository.recordDelivery(
                ReminderKind.PENDING_REMINDER,
                taskId = null,
                at = nowMs - (20 - i) * 60_000L,
            )
        }

        // Run 1: 2 + 1 = 3 → allowed (cap 3).
        runPeriodicWorkerOnce()
        assertEquals(3, container.reminderRepository.countDeliveredSince(ReminderKind.PENDING_REMINDER, 0))

        // Run 2: cap or cooldown blocks → still 3, never 4 (spec: "MUST NOT exceed").
        runPeriodicWorkerOnce()
        assertEquals(3, container.reminderRepository.countDeliveredSince(ReminderKind.PENDING_REMINDER, 0))
    }

    private suspend fun seedTask(container: AppContainer) {
        container.taskRepository.save(
            Task(name = "Read book", type = TaskType.FLEXIBLE, createdAt = System.currentTimeMillis()),
        )
    }

    private fun runPeriodicWorkerOnce() {
        app.container.reminderScheduler.ensurePeriodicReminders()
        val workManager = WorkManager.getInstance(context)
        val infos = workManager.getWorkInfosForUniqueWork(ReminderScheduler.PERIODIC_WORK_NAME).get()
        // Iteration 1 of a fresh periodic work starts immediately on its own
        // (state RUNNING); later iterations wait on the period-delay gate, which
        // the 2.11 TestDriver releases with setPeriodDelayMet. Releasing a gate
        // that is not pending is a no-op, so calling it for any ENQUEUED work is
        // safe. The worker's real Result is used; results can no longer be forged.
        infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }?.let { info ->
            testDriver.setPeriodDelayMet(info.id)
        }
        awaitState(WorkInfo.State.ENQUEUED)
    }

    /** Polls until the unique periodic work is back in [state] (termination proof). */
    private fun awaitState(state: WorkInfo.State, timeoutMs: Long = 30_000) {
        val workManager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (workManager.getWorkInfosForUniqueWork(ReminderScheduler.PERIODIC_WORK_NAME).get()
                    .any { it.state == state }
            ) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for periodic work state $state")
    }
}