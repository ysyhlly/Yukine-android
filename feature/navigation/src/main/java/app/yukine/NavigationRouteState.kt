package app.yukine

import app.yukine.navigation.HomeTab
import app.yukine.navigation.TabRoute

private const val DefaultLibraryMode = "songs"

enum class LibraryPage(val route: String) {
    Overview("overview"),
    Browse("browse");

    companion object {
        fun fromRoute(route: String?): LibraryPage? = entries.firstOrNull { it.route == route }
    }
}

data class NavigationRouteState(
    val selectedTab: TabRoute = HomeTab,
    val libraryPage: LibraryPage = LibraryPage.Overview,
    val libraryMode: String = DefaultLibraryMode,
    val selectedLibraryGroupKey: String = "",
    val selectedLibraryGroupTitle: String = "",
    val selectedPlaylistId: Long = -1L,
    val searchQuery: String = "",
    val networkPage: NetworkPage = NetworkPage.Home,
    val settingsPage: SettingsPage = SettingsPage.Home,
    val selectedRemoteSourceId: Long = -1L
)
