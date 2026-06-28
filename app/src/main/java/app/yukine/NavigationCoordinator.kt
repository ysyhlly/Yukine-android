package app.yukine

import app.yukine.model.Track

internal class NavigationCoordinator(
    private val routeController: MainRouteController,
    private val settingsStore: MainSettingsStore,
    private val libraryStore: MainLibraryStore,
    private val searchViewModel: SearchViewModel,
    private val streamingViewModel: StreamingViewModel,
    private val libraryViewModel: LibraryViewModel,
    private val settingsViewModel: SettingsViewModel,
    private val listener: Listener
) {

    interface Listener {
        fun renderSelectedTab()
        fun renderSelectedTabForNavHostState()
        fun syncNavHostState()
    }

    var scrollContentToTopOnNextRender: Boolean = false

    fun selectedTab(): String = routeController.selectedTab() ?: MainRoutes.TAB_HOME

    fun libraryMode(): String = routeController.libraryMode() ?: LibraryGrouping.SONGS

    fun selectedLibraryGroupKey(): String = routeController.selectedLibraryGroupKey() ?: ""

    fun selectedLibraryGroupTitle(): String = routeController.selectedLibraryGroupTitle() ?: ""

    fun selectedPlaylistId(): Long = routeController.selectedPlaylistId()

    fun searchQuery(): String = routeController.searchQuery() ?: ""

    fun networkPage(): String = routeController.networkPage() ?: MainRoutes.NETWORK_HOME

    fun settingsPage(): String = routeController.settingsPage() ?: MainRoutes.SETTINGS_HOME

    fun selectedRemoteSourceId(): Long = routeController.selectedRemoteSourceId()

    fun navigateToTab(tabKey: String, userInitiated: Boolean = false, renderImmediately: Boolean = false) {
        val normalizedTab = normalizedTabKey(tabKey)
        val previousDirectory = currentDirectoryKey()
        val sameTab = routeController.navigateToTab(normalizedTab, userInitiated)
        if (userInitiated && sameTab && previousDirectory == currentDirectoryKey()) {
            requestCurrentDirectoryScrollToTop()
        }
        routeController.persist()
        if (renderImmediately || sameTab) {
            listener.renderSelectedTab()
        }
    }

    fun navigateNetworkPage(page: String) {
        routeController.setNetworkPage(page)
        renderAndPersistSelectedTab()
    }

    fun navigateToNetworkTabPage(page: String) {
        routeController.setNetworkPage(page)
        navigateToTab(MainRoutes.TAB_NETWORK)
    }

    fun setSelectedPlaylistId(playlistId: Long) {
        routeController.setSelectedPlaylistId(playlistId)
    }

    fun renderAndPersistSelectedTab() {
        routeController.persist()
        listener.renderSelectedTab()
    }

    fun handleAppBack(): Boolean {
        val result = routeController.applyBackNavigation()
        if (!result.handled) return false
        if (result.navigateTab) {
            navigateToTab(result.selectedTab)
        } else {
            renderAndPersistSelectedTab()
        }
        return true
    }

    fun handleContentHorizontalSwipe(next: Boolean): Boolean {
        if (MainRoutes.TAB_LIBRARY != selectedTab()) return false
        if (!next && closeLibraryDetailIfNeeded()) return true

        val modes = librarySwipeModes()
        val currentIndex = modes.indexOf(libraryMode())
        if (currentIndex < 0) return false
        val nextIndex = if (next) currentIndex + 1 else currentIndex - 1
        if (nextIndex < 0 || nextIndex >= modes.size) return false

        routeController.setLibraryMode(modes[nextIndex])
        renderAndPersistSelectedTab()
        return true
    }

    fun applySearch() {
        val query = searchQuery()
        libraryStore.applySearch(query)
        renderAndPersistSelectedTab()
    }

    fun performUnifiedSearch(query: String) {
        val safeQuery = query.trim()
        routeController.setSearchQuery(safeQuery)
        listener.syncNavHostState()
    }

    fun clearUnifiedSearchOnExit() {
        if (searchQuery().isNotEmpty()) {
            routeController.setSearchQuery("")
        }
        searchViewModel.clearSearch()
        streamingViewModel.clearStreamingSearchSession()
        libraryStore.applySearch("")
        listener.renderSelectedTabForNavHostState()
    }

    private fun closeLibraryDetailIfNeeded(): Boolean {
        if (selectedLibraryGroupKey().isEmpty() && selectedPlaylistId() < 0L) return false
        routeController.clearLibraryGroup()
        routeController.setSelectedPlaylistId(-1L)
        renderAndPersistSelectedTab()
        return true
    }

    private fun librarySwipeModes(): List<String> = listOf(
        LibraryGrouping.SONGS,
        LibraryGrouping.ALBUMS,
        LibraryGrouping.ARTISTS,
        LibraryGrouping.FOLDERS,
        LibraryGrouping.PLAYLISTS
    )

    private fun normalizedTabKey(tabKey: String): String =
        if (MainRoutes.TAB_NOW == tabKey) MainRoutes.TAB_HOME else tabKey

    private fun currentDirectoryKey(): String {
        val tab = selectedTab()
        return when {
            MainRoutes.TAB_NETWORK == tab -> "$tab|${networkPage()}|${selectedRemoteSourceId()}"
            MainRoutes.TAB_SETTINGS == tab -> "$tab|${settingsPage()}"
            MainRoutes.TAB_LIBRARY == tab -> "$tab|${libraryMode()}|${selectedLibraryGroupKey()}|${selectedPlaylistId()}"
            else -> tab
        }
    }

    private fun requestCurrentDirectoryScrollToTop() {
        scrollContentToTopOnNextRender = true
        if (MainRoutes.TAB_SETTINGS == selectedTab()) {
            settingsViewModel.scrollToTopOnNextRender()
        }
    }
}
