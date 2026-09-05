package com.remindme.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Central route registry. Phase 5 (UI) populates these with real destinations. */
object Routes {
    const val HOME = "home"
}

/**
 * App-wide navigation host.  The single HOME placeholder screen is replaced by
 * WeekView and friends in Phase 5. This stub keeps the scaffold buildable.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "RemindMe — weekly reminders",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}