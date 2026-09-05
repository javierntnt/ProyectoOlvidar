package com.remindme.domain.use_case

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remindme.data.local.AppDatabase
import com.remindme.data.repository.ReminderRepository
import com.remindme.domain.model.AntiSpamConfig
import com.remindme.domain.model.BlockReason
import com.remindme.domain.model.DeliveryDecision
import com.remindme.domain.model.ReminderKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Anti-spam gate tests (spec: Anti-Spam Daily Cap + Quiet Hours + Notification State
 * "delivered state is restored after restart"). Tasks 3.3 / 3.4 / 6.1 — RED-first.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AntiSpamGateTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ReminderRepository

    private val utc = TimeZone.getTimeZone("UTC")
    private val dayStart = 1_767_571_200_000L    // 2026-01-05T00:00Z (Monday)
    private val mondayTenAm = 1_767_607_200_000L // 2026-01-05T10:00Z

    private val defaultConfig = AntiSpamConfig(
        dailyCap = 3,
        cooldownMinutes = 240,
        quietStartMinute = 1380, // 23:00
        quietEndMinute = 420,    // 07:00
    )

    private fun gate(config: AntiSpamConfig = defaultConfig) = AntiSpamGate(repository) { config }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReminderRepository(db.reminderLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `allows delivery when under the cap, cooldown elapsed and outside quiet hours`() = runTest {
        val decision = gate().canDeliver(ReminderKind.PENDING_REMINDER, mondayTenAm, utc)
        assertTrue(decision is DeliveryDecision.Allowed)
    }

    @Test
    fun `blocks when the daily cap is reached`() = runTest {
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + 3_600_000L)
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + 7_200_000L)
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + 10_800_000L)

        val decision = gate().canDeliver(ReminderKind.PENDING_REMINDER, mondayTenAm, utc)
        assertEquals(BlockReason.DAILY_CAP_REACHED, (decision as DeliveryDecision.Blocked).reason)
    }

    @Test
    fun `cap resets on a new day`() = runTest {
        // Yesterday's deliveries must NOT count against today's cap (spec: "per day").
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart - 1_000L)
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart - 60_000L)

        val tuesdayTenAm = dayStart + 86_400_000L + 10 * 3_600_000L
        val decision = gate().canDeliver(ReminderKind.PENDING_REMINDER, tuesdayTenAm, utc)
        assertTrue(decision is DeliveryDecision.Allowed)
    }

    @Test
    fun `blocks during the cooldown window and allows after it elapses`() = runTest {
        // Last delivery 2 hours ago < 240 min cooldown → blocked.
        repository.recordDelivery(ReminderKind.PENDING_REMINDER, null, mondayTenAm - 7_200_000L)
        val blocked = gate().canDeliver(ReminderKind.PENDING_REMINDER, mondayTenAm, utc)
        assertEquals(BlockReason.COOLDOWN_ACTIVE, (blocked as DeliveryDecision.Blocked).reason)

        // A second scenario: last delivery 5 hours ago > 240 min cooldown → allowed.
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshRepository = ReminderRepository(freshDb.reminderLogDao())
            freshRepository.recordDelivery(ReminderKind.PENDING_REMINDER, null, mondayTenAm - 5 * 3_600_000L)
            val allowed = AntiSpamGate(freshRepository) { defaultConfig }.canDeliver(
                ReminderKind.PENDING_REMINDER,
                mondayTenAm,
                utc,
            )
            assertTrue(allowed is DeliveryDecision.Allowed)
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun `blocks during quiet hours`() = runTest {
        val oneAm = dayStart + 3_600L
        val decision = gate().canDeliver(ReminderKind.PENDING_REMINDER, oneAm, utc)
        assertEquals(BlockReason.QUIET_HOURS, (decision as DeliveryDecision.Blocked).reason)
    }

    @Test
    fun `cap is enforced across database close and reopen (restart)`() = runTest {
        val dbName = "gate_restart_${System.nanoTime()}.db"
        try {
            // "First process run": two deliveries today, then the database closes.
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
                .also { first ->
                    val repo = ReminderRepository(first.reminderLogDao())
                    repo.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + 60_000L)
                    repo.recordDelivery(ReminderKind.PENDING_REMINDER, null, dayStart + 120_000L)
                    first.close()
                }

            // "App restart": re-open the same file — the cap must still hold (spec
            // Notification State: "delivered state is restored after restart").
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            try {
                val capTwo = AntiSpamConfig(dailyCap = 2, cooldownMinutes = 240, quietStartMinute = 1380, quietEndMinute = 420)
                val gateAfterRestart = AntiSpamGate(ReminderRepository(reopened.reminderLogDao())) { capTwo }
                val decision = gateAfterRestart.canDeliver(ReminderKind.PENDING_REMINDER, mondayTenAm, utc)
                assertEquals(BlockReason.DAILY_CAP_REACHED, (decision as DeliveryDecision.Blocked).reason)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}