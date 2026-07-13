package app.yukine

import androidx.lifecycle.SavedStateHandle
import app.yukine.navigation.HomeTab
import app.yukine.navigation.TabRoute

/** Persists only typed navigation state; feature policy remains outside the Activity. */
object NavigationRouteStateStore {
    private const val SELECTED_TAB = "selectedTab"
    private const val LIBRARY_MODE = "libraryMode"
    private const val SELECTED_LIBRARY_GROUP_KEY = "selectedLibraryGroupKey"
    private const val SELECTED_LIBRARY_GROUP_TITLE = "selectedLibraryGroupTitle"
    private const val SELECTED_PLAYLIST_ID = "selectedPlaylistId"
    private const val SEARCH_QUERY = "searchQuery"
    private const val NETWORK_PAGE = "networkPage"
    private const val SETTINGS_PAGE = "settingsPage"
    private const val SELECTED_REMOTE_SOURCE_ID = "selectedRemoteSourceId"
    private const val LEGACY_HOME_LIBRARY_MODE = "home"
    private const val DEFAULT_LIBRARY_MODE = "songs"

    fun restore(savedStateHandle: SavedStateHandle): NavigationRouteState {
        val restoredTab = savedStateHandle[SELECTED_TAB] ?: MainRoutes.TAB_HOME
        val restoredLibraryMode = savedStateHandle[LIBRARY_MODE] ?: DEFAULT_LIBRARY_MODE
        val selectedTab = when {
            restoredTab == MainRoutes.TAB_NOW -> HomeTab
            restoredTab == MainRoutes.TAB_LIBRARY && restoredLibraryMode == LEGACY_HOME_LIBRARY_MODE -> HomeTab
            else -> TabRoute.fromKey(restoredTab) ?: HomeTab
        }
        return NavigationRouteState(
            selectedTab = selectedTab,
            libraryMode = restoredLibraryMode.takeUnless { it == LEGACY_HOME_LIBRARY_MODE }
                ?: DEFAULT_LIBRARY_MODE,
            selectedLibraryGroupKey = savedStateHandle[SELECTED_LIBRARY_GROUP_KEY] ?: "",
            selectedLibraryGroupTitle = savedStateHandle[SELECTED_LIBRARY_GROUP_TITLE] ?: "",
            selectedPlaylistId = savedStateHandle[SELECTED_PLAYLIST_ID] ?: -1L,
            searchQuery = savedStateHandle[SEARCH_QUERY] ?: "",
            networkPage = savedStateHandle[NETWORK_PAGE] ?: MainRoutes.NETWORK_HOME,
            settingsPage = SettingsPage.fromRoute(savedStateHandle[SETTINGS_PAGE]),
            selectedRemoteSourceId = savedStateHandle[SELECTED_REMOTE_SOURCE_ID] ?: -1L
        )
    }

    fun save(savedStateHandle: SavedStateHandle, state: NavigationRouteState) {
        savedStateHandle[SELECTED_TAB] = state.selectedTab.route
        savedStateHandle[LIBRARY_MODE] = state.libraryMode
        savedStateHandle[SELECTED_LIBRARY_GROUP_KEY] = state.selectedLibraryGroupKey
        savedStateHandle[SELECTED_LIBRARY_GROUP_TITLE] = state.selectedLibraryGroupTitle
        savedStateHandle[SELECTED_PLAYLIST_ID] = state.selectedPlaylistId
        savedStateHandle[SEARCH_QUERY] = state.searchQuery
        savedStateHandle[NETWORK_PAGE] = state.networkPage
        savedStateHandle[SETTINGS_PAGE] = state.settingsPage.route
        savedStateHandle[SELECTED_REMOTE_SOURCE_ID] = state.selectedRemoteSourceId
    }
}
