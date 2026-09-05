package com.remindme

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.remindme.navigation.AppNavHost
import com.remindme.notifications.NotificationChannels
import com.remindme.ui.theme.RemindMeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single-Activity host. All screens are Compose destinations inside [AppNavHost].
 * On startup it creates the notification channels, asks for POST_NOTIFICATIONS
 * on Android 13+, and (re)arms the periodic worker and advance alarms.
 */
class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result handled by OS */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationChannels.create(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as RemindMeApp).container
        appScope.launch {
            runCatching {
                container.reminderScheduler.ensurePeriodicReminders()
                container.reminderScheduler.rescheduleAdvanceAlerts()
            }
        }

        setContent {
            RemindMeTheme {
                AppNavHost()
            }
        }
    }
}