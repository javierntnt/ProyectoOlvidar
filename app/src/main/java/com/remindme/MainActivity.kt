package com.remindme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.remindme.navigation.AppNavHost
import com.remindme.ui.theme.RemindMeTheme

/**
 * Single-Activity host. All screens are Compose destinations inside [AppNavHost].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemindMeTheme {
                AppNavHost()
            }
        }
    }
}