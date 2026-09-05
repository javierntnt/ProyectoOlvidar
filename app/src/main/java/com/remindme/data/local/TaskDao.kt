package com.remindme.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Observe all tasks on a given day of the week (1=Mon … 7=Sun), ordered by time. */
    @Query("SELECT * FROM tasks WHERE dayOfWeek = :day ORDER BY timeMinute ASC, name ASC")
    fun tasksForDay(day: Int): Flow<List<TaskEntity>>

    /** Observe every task (used in the overview / week view). */
    @Query("SELECT * FROM tasks ORDER BY dayOfWeek ASC, timeMinute ASC, name ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Observe a single task by id. */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    /** One-shot read (suspending). */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** Observe all tasks of a specific type. */
    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY dayOfWeek ASC, timeMinute ASC")
    fun observeByType(type: TaskType): Flow<List<TaskEntity>>

    /** One-shot read: all incomplete tasks (for worker scan). */
    @Query("SELECT * FROM tasks WHERE done = 0")
    suspend fun getAllPending(): List<TaskEntity>

    /** Insert or replace. When id is 0 Room auto-generates; otherwise replaces in place. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}