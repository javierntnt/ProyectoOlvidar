package com.remindme.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS handling (spec: Notification Permission — Android 13+
 * requires the runtime prompt; below that the permission is granted by
 * installation). Callers check this before posting, so the app degrades
 * gracefully instead of crashing.
 */
object NotificationPermissionHelper {

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}