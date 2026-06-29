package app.yukine

private const val DefaultLibraryMode = "songs"

data class NavigationRouteState(
    val selectedTab: String = MainRoutes.TAB_HOME,
    val libraryMode: String = DefaultLibraryMode,
    val selectedLibraryGroupKey: String = "",
    val selectedLibraryGroupTitle: String = "",
    val selectedPlaylistId: Long = -1L,
    val searchQuery: String = "",
    val networkPage: String = MainRoutes.NETWORK_HOME,
    val settingsPage: String = MainRoutes.SETTINGS_HOME,
    val selectedRemoteSourceId: Long = -1L
)
