package com.remindme.data.repository

import com.remindme.data.local.TaskDao
import com.remindme.data.local.TaskEntity
import com.remindme.domain.model.Task
import com.remindme.domain.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for tasks.  Thin mapper over the DAO;
 * repositories are the only callers of the entity ↔ domain conversion.
 */
class TaskRepository(private val taskDao: TaskDao) {

    // ---- observable flows ---------------------------------------------------

    fun tasksForDay(day: Int): Flow<List<Task>> =
        taskDao.tasksForDay(day).map { list -> list.map(TaskEntity::toDomain) }

    fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map(TaskEntity::toDomain) }

    fun observeById(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    fun observeByType(type: TaskType): Flow<List<Task>> =
        taskDao.observeByType(type).map { list -> list.map(TaskEntity::toDomain) }

    // ---- one-shot reads (suspend) -------------------------------------------

    suspend fun getById(id: Long): Task? = taskDao.getById(id)?.toDomain()

    suspend fun getAllPending(): List<Task> = taskDao.getAllPending().map(TaskEntity::toDomain)

    // ---- mutations ----------------------------------------------------------

    suspend fun save(task: Task): Long = taskDao.upsert(TaskEntity.fromDomain(task))

    suspend fun delete(id: Long) = taskDao.deleteById(id)
}