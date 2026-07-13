package app.yukine

import app.yukine.navigation.HomeTab
import app.yukine.navigation.NowTab
import app.yukine.navigation.TabRoute

internal class MainRouteController(
    private val viewModel: NavigationViewModel
) : LibraryRouteActions {
    /** NavigationViewModel is the sole owner of the persistent route snapshot. */
    private val state: NavigationRouteState
        get() = viewModel.state.value
    private var networkEntry: NetworkEntry? = null

    init {
        restoreFromViewModel()
    }

    fun current(): NavigationRouteState {
        return state
    }

    fun restoreFromViewModel() {
        // Kept as an explicit lifecycle hook for the Java host. State is read directly from the
        // ViewModel, so there is no second mutable copy to synchronize.
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
    ): NavigationRouteState {
        return NavigationRouteState(
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

    fun persist(state: NavigationRouteState) {
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

    /** Typed settings boundary; the route string is kept only for saved-state compatibility. */
    fun settingsPageModel(): SettingsPage = SettingsPage.fromRoute(state.settingsPage)

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

    fun navigateToTab(tab: TabRoute, userInitiated: Boolean): Boolean {
        val normalizedTab = normalizeTab(tab).route
        val sameTab = normalizedTab == selectedTab()
        if (userInitiated || (selectedTab() == MainRoutes.TAB_NETWORK && normalizedTab != MainRoutes.TAB_NETWORK)) {
            networkEntry = null
        }
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

    fun navigateToNetworkPageFromCurrent(networkPage: String) {
        if (selectedTab() != MainRoutes.TAB_NETWORK) {
            networkEntry = NetworkEntry(state, networkPage)
        }
        update(
            MainRoutes.TAB_NETWORK,
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

    override fun setLibraryMode(libraryMode: String) {
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

    override fun selectLibraryGroup(key: String, title: String) {
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

    override fun clearLibraryGroup() {
        selectLibraryGroup("", "")
    }

    override fun setSelectedPlaylistId(selectedPlaylistId: Long) {
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

    override fun setSearchQuery(searchQuery: String) {
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

    fun setSettingsPage(page: SettingsPage) {
        setSettingsPage(page.route)
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
        val entry = networkEntry
        if (
            entry != null &&
            selectedTab() == MainRoutes.TAB_NETWORK &&
            networkPage() == entry.entryPage
        ) {
            viewModel.updateRoute(entry.origin)
            networkEntry = null
            return MainBackNavigationPolicy.Result.navigate(
                entry.origin.selectedTab,
                entry.origin.networkPage,
                entry.origin.settingsPage,
                true
            )
        }
        val result = MainBackNavigationPolicy.resolve(
            selectedTab(),
            networkPage(),
            SettingsPage.fromRoute(settingsPage()),
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

    private data class NetworkEntry(
        val origin: NavigationRouteState,
        val entryPage: String
    )

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
        viewModel.updateRoute(snapshot(
            normalizeTabKey(selectedTab),
            normalizeLibraryMode(libraryMode),
            selectedLibraryGroupKey,
            selectedLibraryGroupTitle,
            selectedPlaylistId,
            searchQuery,
            networkPage,
            settingsPage,
            selectedRemoteSourceId
        ))
    }

    private fun normalizeTab(tab: TabRoute): TabRoute {
        return if (tab == NowTab) HomeTab else tab
    }

    private fun normalizeTabKey(tabKey: String): String {
        return TabRoute.fromKey(tabKey)?.let(::normalizeTab)?.route ?: HomeTab.route
    }

    private fun normalizeLibraryMode(mode: String): String {
        return if (mode == LibraryGrouping.HOME) LibraryGrouping.SONGS else mode
    }
}
