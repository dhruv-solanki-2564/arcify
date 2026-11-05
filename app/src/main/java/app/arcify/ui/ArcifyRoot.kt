package app.arcify.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.arcify.R
import app.arcify.ui.focus.FocusRoute

private enum class TopLevel(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    FOCUS("focus", R.string.nav_focus, Icons.Filled.PlayArrow),
    BLOCK("block", R.string.nav_block, Icons.Filled.Lock),
    SCHEDULE("schedule", R.string.nav_schedule, Icons.Filled.DateRange),
    INSIGHTS("insights", R.string.nav_insights, Icons.Filled.Info),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun ArcifyRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevel.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevel.FOCUS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevel.FOCUS.route) { FocusRoute() }
            composable(TopLevel.BLOCK.route) { PlaceholderScreen(R.string.nav_block, R.string.placeholder_block) }
            composable(TopLevel.SCHEDULE.route) {
                PlaceholderScreen(R.string.nav_schedule, R.string.placeholder_schedule)
            }
            composable(TopLevel.INSIGHTS.route) {
                PlaceholderScreen(R.string.nav_insights, R.string.placeholder_insights)
            }
            composable(TopLevel.SETTINGS.route) {
                PlaceholderScreen(R.string.nav_settings, R.string.placeholder_settings)
            }
        }
    }
}
