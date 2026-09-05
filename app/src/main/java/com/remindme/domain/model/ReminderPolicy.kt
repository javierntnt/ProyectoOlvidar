package com.remindme.domain.model

/**
 * Anti-spam policy inputs for the delivery gate (spec: Anti-Spam Daily Cap +
 * Quiet Hours). No defaults live here on purpose — the container resolves them
 * from ReminderPrefs so tests can inject fixed values.
 */
data class AntiSpamConfig(
    /** Max deliveries of one kind per local day (default 3/day). */
    val dailyCap: Int,
    /** Minimum minutes between two deliveries of one kind (default 240 = 4 h). */
    val cooldownMinutes: Int,
    /** Quiet-window start, minutes from midnight (default 1380 = 23:00). */
    val quietStartMinute: Int,
    /** Quiet-window end, minutes from midnight (default 420 = 07:00). */
    val quietEndMinute: Int,
)

/** Verdict of the anti-spam gate for a single delivery attempt. */
sealed interface DeliveryDecision {
    data object Allowed : DeliveryDecision
    data class Blocked(val reason: BlockReason) : DeliveryDecision
}

enum class BlockReason {
    DAILY_CAP_REACHED,
    COOLDOWN_ACTIVE,
    QUIET_HOURS,
}

/** Outcome of EvaluateReminders: whether the worker may post, plus the candidates. */
data class ReminderEvaluation(
    val allowed: Boolean,
    val blockedReason: BlockReason?,
    val pendingTasks: List<Task>,
)