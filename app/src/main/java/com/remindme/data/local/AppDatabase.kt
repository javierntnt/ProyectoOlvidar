package com.remindme.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database — schema v1 (greenfield, no migrations needed).
 *
 * KSP generates the DAO implementations; schema JSON is exported to
 * `app/schemas/` for future migration reference.
 */
@Database(
    entities = [
        TaskEntity::class,
        ReminderLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun reminderLogDao(): ReminderLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        /**
         * Drops the process-wide singleton. Robolectric gives each test method a
         * fresh filesystem but keeps one class loader, so the stale instance would
         * otherwise point at a deleted database file.
         */
        @VisibleForTesting
        fun resetInstanceForTest() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "remindme.db",
            ).build()
    }
}