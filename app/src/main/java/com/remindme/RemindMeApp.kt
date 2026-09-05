package com.remindme

import android.app.Application
import android.content.Context
import com.remindme.data.local.AppDatabase
import com.remindme.data.prefs.ReminderPrefs
import com.remindme.data.repository.ReminderRepository
import com.remindme.data.repository.TaskRepository

/**
 * Application entry point. Holds the manual dependency-injection container
 * (open question 1: manual constructor DI for slice 1; Hilt deferred).
 */
class RemindMeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
}