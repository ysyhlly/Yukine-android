package app.yukine

internal class MainRouteController(
    private val viewModel: NavigationViewModel
) {
    private var state: MainActivityRouteState = MainActivityRouteState()

    init {
        restoreFromViewModel()
    }

    fun current(): MainActivityRouteState {
        return state
    }

    fun restoreFromViewModel() {
        state = viewModel.state.value
    }

    fun snapshot(
        selectedTab: String,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        selectedPlaylistId: Long,
        searchQuery: String,
        networkPage: String,
        settingsPage: String,
        selectedRemoteSourceId: Long
    ): MainActivityRouteState {
        return MainActivityRouteState(
            selectedTab,
            libraryMode,
            selectedLibraryGroupKey,
            selectedLibraryGroupTitle,
            selectedPlaylistId,
            searchQuery,
            networkPage,
            settingsPage,
            selectedRemoteSourceId
        )
    }

    fun persist(state: MainActivityRouteState) {
        this.state = state
        viewModel.updateRoute(state)
    }

    fun persist() {
        viewModel.updateRoute(state)
    }

    fun persist(
        selectedTab: String,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        selectedPlaylistId: Long,
        searchQuery: String,
        networkPage: String,
        settingsPage: String,
        selectedRemoteSourceId: Long
    ) {
        persist(
            snapshot(
                selectedTab,
                libraryMode,
                selectedLibraryGroupKey,
                selectedLibraryGroupTitle,
                selectedPlaylistId,
                searchQuery,
                networkPage,
                settingsPage,
                selectedRemoteSourceId
            )
        )
    }

    fun selectedTab(): String {
        return state.selectedTab
    }

    fun libraryMode(): String {
        return state.libraryMode
    }

    fun selectedLibraryGroupKey(): String {
        return state.selectedLibraryGroupKey
    }

    fun selectedLibraryGroupTitle(): String {
        return state.selectedLibraryGroupTitle
    }

    fun selectedPlaylistId(): Long {
        return state.selectedPlaylistId
    }

    fun searchQuery(): String {
        return state.searchQuery
    }

    fun networkPage(): String {
        return state.networkPage
    }

    fun settingsPage(): String {
        return state.settingsPage
    }

    fun selectedRemoteSourceId(): Long {
        return state.selectedRemoteSourceId
    }

    fun setSelectedTab(selectedTab: String) {
        update(
            selectedTab,
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery(),
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun navigateToTab(tabKey: String, userInitiated: Boolean): Boolean {
        val normalizedTab = normalizeTab(tabKey)
        val sameTab = normalizedTab == selectedTab()
        var nextNetworkPage = networkPage()
        var nextSettingsPage = settingsPage()
        var nextRemoteSourceId = selectedRemoteSourceId()
        var nextLibraryMode = libraryMode()
        if (userInitiated && MainRoutes.TAB_NETWORK == normalizedTab) {
            nextNetworkPage = MainRoutes.NETWORK_HOME
            nextRemoteSourceId = -1L
        } else if (userInitiated && MainRoutes.TAB_SETTINGS == normalizedTab) {
            nextSettingsPage = MainRoutes.SETTINGS_HOME
        } else if (userInitiated && MainRoutes.TAB_LIBRARY == normalizedTab && nextLibraryMode == LibraryGrouping.HOME) {
            nextLibraryMode = LibraryGrouping.SONGS
        }
        update(
            normalizedTab,
            nextLibraryMode,
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery(),
            nextNetworkPage,
            nextSettingsPage,
            nextRemoteSourceId
        )
        return sameTab
    }

    fun setLibraryMode(libraryMode: String) {
        update(
            selectedTab(),
            normalizeLibraryMode(libraryMode),
            "",
            "",
            -1L,
            searchQuery(),
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun selectLibraryGroup(key: String, title: String) {
        update(
            selectedTab(),
            libraryMode(),
            key,
            title,
            selectedPlaylistId(),
            searchQuery(),
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun clearLibraryGroup() {
        selectLibraryGroup("", "")
    }

    fun setSelectedPlaylistId(selectedPlaylistId: Long) {
        update(
            selectedTab(),
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId,
            searchQuery(),
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun setSearchQuery(searchQuery: String) {
        update(
            selectedTab(),
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery,
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun setNetworkPage(networkPage: String) {
        update(
            selectedTab(),
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery(),
            networkPage,
            settingsPage(),
            selectedRemoteSourceId()
        )
    }

    fun setSettingsPage(settingsPage: String) {
        update(
            selectedTab(),
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery(),
            networkPage(),
            settingsPage,
            selectedRemoteSourceId()
        )
    }

    fun clearSelectedRemoteSource() {
        setSelectedRemoteSourceId(-1L)
    }

    fun setSelectedRemoteSourceId(selectedRemoteSourceId: Long) {
        update(
            selectedTab(),
            libraryMode(),
            selectedLibraryGroupKey(),
            selectedLibraryGroupTitle(),
            selectedPlaylistId(),
            searchQuery(),
            networkPage(),
            settingsPage(),
            selectedRemoteSourceId
        )
    }

    fun applyBackNavigation(): MainBackNavigationPolicy.Result {
        val result = MainBackNavigationPolicy.resolve(
            selectedTab(),
            networkPage(),
            settingsPage(),
            selectedLibraryGroupKey(),
            selectedPlaylistId()
        )
        if (!result.handled) {
            return result
        }
        val nextSelectedTab = if (result.navigateTab) result.selectedTab else selectedTab()
        val nextLibraryGroupKey = if (result.clearLibraryGroup) "" else selectedLibraryGroupKey()
        val nextLibraryGroupTitle = if (result.clearLibraryGroup) "" else selectedLibraryGroupTitle()
        val nextRemoteSourceId = if (result.clearSelectedRemoteSource) -1L else selectedRemoteSourceId()
        val nextSelectedPlaylistId = if (result.clearSelectedPlaylist) -1L else selectedPlaylistId()
        update(
            nextSelectedTab,
            libraryMode(),
            nextLibraryGroupKey,
            nextLibraryGroupTitle,
            nextSelectedPlaylistId,
            searchQuery(),
            result.networkPage,
            result.settingsPage,
            nextRemoteSourceId
        )
        return result
    }

    private fun update(
        selectedTab: String,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        selectedPlaylistId: Long,
        searchQuery: String,
        networkPage: String,
        settingsPage: String,
        selectedRemoteSourceId: Long
    ) {
        state = snapshot(
            normalizeTab(selectedTab),
            normalizeLibraryMode(libraryMode),
            selectedLibraryGroupKey,
            selectedLibraryGroupTitle,
            selectedPlaylistId,
            searchQuery,
            networkPage,
            settingsPage,
            selectedRemoteSourceId
        )
    }

    private fun normalizeTab(tabKey: String): String {
        return when (tabKey) {
            MainRoutes.TAB_NOW -> MainRoutes.TAB_HOME
            else -> tabKey
        }
    }

    private fun normalizeLibraryMode(mode: String): String {
        return if (mode == LibraryGrouping.HOME) LibraryGrouping.SONGS else mode
    }
}
