package com.remindme.domain.use_case

import com.remindme.data.repository.ReminderRepository
import com.remindme.domain.model.AntiSpamConfig
import com.remindme.domain.model.BlockReason
import com.remindme.domain.model.DeliveryDecision
import com.remindme.domain.model.ReminderKind
import com.remindme.domain.time.TimeUtils
import java.util.TimeZone

/**
 * Anti-spam delivery gate (spec: Anti-Spam Daily Cap + Quiet Hours + Notification
 * State "delivered state is restored after restart").
 *
 * A delivery is allowed only when ALL of the following hold for the [ReminderKind]:
 * 1. fewer than [AntiSpamConfig.dailyCap] deliveries already happened today,
 * 2. the last delivery is older than [AntiSpamConfig.cooldownMinutes],
 * 3. the local time is outside the quiet window.
 *
 * The ledger lives in Room, so the cap/cooldown survive app restarts. The
 * container resolves [AntiSpamConfig] from ReminderPrefs at call time, merging
 * the reminder interval into the cooldown so the periodic worker cannot spam
 * faster than the configured cadence.
 */
class AntiSpamGate(
    private val reminderRepository: ReminderRepository,
    private val configProvider: suspend () -> AntiSpamConfig,
) {

    suspend fun canDeliver(kind: ReminderKind, nowEpochMillis: Long, timeZone: TimeZone): DeliveryDecision {
        val config = configProvider()

        val dayStart = TimeUtils.startOfDay(nowEpochMillis, timeZone)
        if (reminderRepository.countDeliveredSince(kind, dayStart) >= config.dailyCap) {
            return DeliveryDecision.Blocked(BlockReason.DAILY_CAP_REACHED)
        }

        val latest = reminderRepository.latestDeliveryAt(kind)
        if (latest != null && (nowEpochMillis - latest) < config.cooldownMinutes * 60_000L) {
            return DeliveryDecision.Blocked(BlockReason.COOLDOWN_ACTIVE)
        }

        if (QuietHours.isInsideQuietWindow(
                TimeUtils.minuteOfDay(nowEpochMillis, timeZone),
                config.quietStartMinute,
                config.quietEndMinute,
            )
        ) {
            return DeliveryDecision.Blocked(BlockReason.QUIET_HOURS)
        }

        return DeliveryDecision.Allowed
    }
}