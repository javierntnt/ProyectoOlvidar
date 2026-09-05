package com.remindme

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.remindme.data.local.AppDatabase
import com.remindme.data.prefs.ReminderPrefs
import com.remindme.data.repository.ReminderRepository
import com.remindme.data.repository.TaskRepository
import com.remindme.domain.model.AntiSpamConfig
import com.remindme.domain.use_case.AntiSpamGate
import com.remindme.domain.use_case.CreateTask
import com.remindme.domain.use_case.DeleteTask
import com.remindme.domain.use_case.EvaluateReminders
import com.remindme.domain.use_case.UpdateTask
import com.remindme.notifications.ReminderNotifier
import com.remindme.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Application entry point. Holds the manual dependency-injection container
 * (open question 1: manual constructor DI for slice 1; Hilt deferred).
 *
 * Open so Robolectric tests can subclass it (see TestRemindMeApp): debug builds
 * remove the androidx.startup provider (see app/src/debug/AndroidManifest.xml)
 * and initialize WorkManager here; release builds keep provider auto-init and
 * this becomes a no-op inside the runCatching guard.
 */
open class RemindMeApp : Application() {

    /**
     * Lazy so Robolectric can drop the process-wide Room singleton in `@Before`
     * before the container is first touched, and so a debug build can install
     * the WorkManager TestDriver before any worker is scheduled.
     */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        initializeWorkManager()
    }

    /**
     * Overridable so tests can prevent WorkManager initialization and install
     * the WorkManager TestDriver instead.
     */
    protected open fun initializeWorkManager() {
        runCatching {
            WorkManager.initialize(this, Configuration.Builder().build())
        }
    }
}

/**
 * Manual DI container. Simple, explicit, and easy to replace with Hilt in a later
 * slice. Wired lazily: repositories are cheap wrappers over Room DAOs.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Lazily-created singleton Room database. */
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    /** User-controllable reminder settings (DataStore-backed). */
    val reminderPrefs: ReminderPrefs by lazy { ReminderPrefs(appContext) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(database.reminderLogDao())
    }

    // ---- use cases ------------------------------------------------------------

    val createTask: CreateTask by lazy { CreateTask(taskRepository) }
    val updateTask: UpdateTask by lazy { UpdateTask(taskRepository) }
    val deleteTask: DeleteTask by lazy { DeleteTask(taskRepository) }
    val antiSpamGate: AntiSpamGate by lazy { AntiSpamGate(reminderRepository, antiSpamConfigProvider) }
    val evaluateReminders: EvaluateReminders by lazy { EvaluateReminders(taskRepository, antiSpamGate) }

    // ---- notifications ---------------------------------------------------------

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(appContext) }
    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext, reminderPrefs, taskRepository)
    }

    /**
     * Resolves the anti-spam policy from DataStore at call time so edits apply
     * without a restart. The reminder interval is folded into the cooldown:
     * the periodic worker may only fire as often as the slowest of the user's
     * two knobs allows.
     */
    val antiSpamConfigProvider: suspend () -> AntiSpamConfig = {
        AntiSpamConfig(
            dailyCap = reminderPrefs.dailyCap.first(),
            cooldownMinutes = maxOf(
                reminderPrefs.cooldownMinutes.first(),
                reminderPrefs.reminderIntervalMinutes.first(),
            ),
            quietStartMinute = reminderPrefs.quietStartMinute.first(),
            quietEndMinute = reminderPrefs.quietEndMinute.first(),
        )
    }
}