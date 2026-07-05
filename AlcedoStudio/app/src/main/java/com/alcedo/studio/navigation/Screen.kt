package com.alcedo.studio.navigation

import com.alcedo.studio.core.Constants

sealed class Screen(val route: String) {
    object Album : Screen("album")
    object Editor : Screen("editor/{${Constants.Navigation.ARG_URI}}") {
        fun createRoute(uri: String): String = "editor/$uri"
    }
    object Settings : Screen("settings")
    object Presets : Screen("presets")
    object Favorites : Screen("favorites")

    companion object {
        val ALBUM_ROUTE = Album.route
        val EDITOR_ROUTE = Editor.route
        val SETTINGS_ROUTE = Settings.route
        val PRESETS_ROUTE = Presets.route
        val FAVORITES_ROUTE = Favorites.route
    }
}
