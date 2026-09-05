package com.remindme.domain.model

/** Three task types as defined in the spec's three-task-types requirement. */
enum class TaskType { FIXED, FLEXIBLE, HABIT }

/** Frequency target for habit tasks. */
enum class HabitFrequency { DAILY, WEEKLY }

/** Distinguish the two notification kinds delivered via [ReminderLogEntity]. */
enum class ReminderKind { ADVANCE_ALERT, PENDING_REMINDER }

/**
 * Domain-level task model.  Maps one-to-one with [TaskEntity] via
 * [toDomain]/[fromDomain] in the data layer.
 *
 * Edge-case: habit tasks without progress show 0 done hours (spec: Habit Target Hours).
 */
data class Task(
    val id: Long = 0,
    val name: String,
    val type: TaskType,
    /** 1–7 (Monday–Sunday).  Required for FIXED and HABIT, null for FLEXIBLE. */
    val dayOfWeek: Int? = null,
    /** Minutes from midnight (0–1439).  FIXED only. */
    val timeMinute: Int? = null,
    /** HABIT only: daily or weekly frequency. */
    val habitFrequency: HabitFrequency? = null,
    /** HABIT only: target hours per frequency period. */
    val habitTargetHours: Int? = null,
    /** HABIT only: hours already completed this period (manual stepper, default 0). */
    val habitDoneHours: Int = 0,
    /** True when the user marks the task as completed for the period. */
    val done: Boolean = false,
    /** Epoch milliseconds when the task was created. */
    val createdAt: Long,
)