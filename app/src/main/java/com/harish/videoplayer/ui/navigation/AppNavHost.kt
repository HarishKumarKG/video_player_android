package com.harish.videoplayer.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.harish.videoplayer.ui.home.HomeRoute
import com.harish.videoplayer.ui.player.PlayerRoute
import com.harish.videoplayer.ui.recent.RecentScreen
import com.harish.videoplayer.ui.settings.SettingsScreen
import com.harish.videoplayer.ui.themepicker.ThemeScreen
import kotlinx.coroutines.launch

/**
 * One burger-menu drawer shared by the list screens. Screens only get `onMenuClick`
 * to open it; the drawer decides where to navigate. The player is full screen, so the
 * drawer's edge swipe is switched off there (it would fight with the seek gesture).
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Destination.Home.route,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val openPlayer: () -> Unit = { navController.navigate(Destination.Player.route) { launchSingleTop = true } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe only CLOSES the open menu. A closed menu opens with the ☰ button, so a swipe from
        // the left edge is Android's "back" gesture again instead of pulling the menu out.
        gesturesEnabled = drawerState.isOpen && currentRoute != Destination.Player.route,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                // true while opening/open, so the profile slideshow only runs when visible
                isOpen = drawerState.targetValue == DrawerValue.Open,
                onDestinationClick = { destination ->
                    scope.launch { drawerState.close() }
                    if (destination.route != currentRoute) {
                        navController.navigate(destination.route) {
                            // Videos stays at the bottom of the stack, so Back always returns there
                            popUpTo(Destination.Home.route)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(route = Destination.Home.route) {
                HomeRoute(
                    onMenuClick = openDrawer,
                    onOpenPlayer = openPlayer,
                    onOpenRecent = {
                        navController.navigate(Destination.Recent.route) { launchSingleTop = true }
                    }
                )
            }
            composable(route = Destination.Recent.route) {
                RecentScreen(onMenuClick = openDrawer, onOpenPlayer = openPlayer)
            }
            composable(route = Destination.Settings.route) {
                SettingsScreen(onMenuClick = openDrawer)
            }
            composable(route = Destination.Theme.route) {
                ThemeScreen(onMenuClick = openDrawer)
            }
            composable(route = Destination.Player.route) {
                PlayerRoute(onClose = { navController.popBackStack() })
            }
        }
    }
}
