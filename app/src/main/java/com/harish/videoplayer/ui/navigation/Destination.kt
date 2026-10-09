package com.harish.videoplayer.ui.navigation

/** Screens of the app. All but Player are in the burger menu; Player opens when a video is tapped. */
sealed class Destination(val route: String) {
    data object Home : Destination(route = "home")
    data object Recent : Destination(route = "recent")
    data object Settings : Destination(route = "settings")
    data object Theme : Destination(route = "theme")
    data object Player : Destination(route = "player")
}
