package app.yukine

import app.yukine.navigation.HomeTab
import app.yukine.navigation.TabRoute

private const val DefaultLibraryMode = "songs"

data class NavigationRouteState(
    val selectedTab: TabRoute = HomeTab,
    val libraryMode: String = DefaultLibraryMode,
    val selectedLibraryGroupKey: String = "",
    val selectedLibraryGroupTitle: String = "",
    val selectedPlaylistId: Long = -1L,
    val searchQuery: String = "",
    val networkPage: NetworkPage = NetworkPage.Home,
    val settingsPage: SettingsPage = SettingsPage.Home,
    val selectedRemoteSourceId: Long = -1L
)
