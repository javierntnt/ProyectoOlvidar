package com.remindme.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remindme.R
import com.remindme.ui.settings.SettingsScreen
import com.remindme.ui.task.TaskFormScreen
import com.remindme.ui.week.WeekScreen

/** Central route registry (Phase 5: week, task form, settings). */
object Routes {
    const val WEEK = "week"
    const val SETTINGS = "settings"

    const val ARG_TASK_ID = "taskId"
    const val TASK_FORM = "task_form?taskId={taskId}"

    /** Build the navigable route string; no query when creating a new task. */
    fun taskForm(taskId: Long? = null): String =
        if (taskId == null) "task_form" else "task_form?taskId=$taskId"
}

/**
 * App-wide navigation host. Week and Settings are top-level tabs (bottom
 * navigation); the task form is a pushed destination rendered without the bar.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute != Routes.TASK_FORM

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WEEK,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.WEEK) {
                WeekScreen(
                    onAddTask = { navController.navigate(Routes.taskForm()) },
                    onEditTask = { taskId -> navController.navigate(Routes.taskForm(taskId)) },
                )
            }
            composable(
                route = Routes.TASK_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_TASK_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val taskId = entry.arguments?.getLong(Routes.ARG_TASK_ID)?.takeIf { it > 0 }
                TaskFormScreen(
                    taskId = taskId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.WEEK,
            onClick = { onNavigate(Routes.WEEK) },
            icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_week)) },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { onNavigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}