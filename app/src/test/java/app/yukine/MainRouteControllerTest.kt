package app.yukine

import androidx.lifecycle.SavedStateHandle
import app.yukine.navigation.LibraryTab
import app.yukine.navigation.NetworkTab
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.TabRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainRouteControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savedStateKeepsLegacyStringsAtSerializationBoundaryOnly() {
        val handle = SavedStateHandle(
            mapOf(
                "selectedTab" to MainRoutes.TAB_SETTINGS,
                "libraryPage" to LibraryPage.Browse.route,
                "settingsPage" to MainRoutes.SETTINGS_APPEARANCE,
                "networkPage" to NetworkPage.Sources.route
            )
        )
        val viewModel = NavigationViewModel(handle)

        assertEquals(SettingsTab, viewModel.state.value.selectedTab)
        assertEquals(LibraryPage.Browse, viewModel.state.value.libraryPage)
        assertEquals(SettingsPage.Appearance, viewModel.state.value.settingsPage)
        assertEquals(NetworkPage.Sources, viewModel.state.value.networkPage)

        viewModel.updateRoute(
            viewModel.state.value.copy(
                selectedTab = NetworkTab,
                settingsPage = SettingsPage.SourcesGroup
            )
        )

        assertEquals(MainRoutes.TAB_NETWORK, handle.get<String>("selectedTab"))
        assertEquals(LibraryPage.Browse.route, handle.get<String>("libraryPage"))
        assertEquals(MainRoutes.SETTINGS_SOURCES_GROUP, handle.get<String>("settingsPage"))
        assertEquals(NetworkPage.Sources.route, handle.get<String>("networkPage"))
    }

    @Test
    fun typedSettingsPageBoundaryKeepsLegacyRouteStorage() {
        val controller = controllerWith(selectedTab = MainRoutes.TAB_SETTINGS)

        controller.setSettingsPage(SettingsPage.Appearance)

        assertEquals(SettingsPage.Appearance, controller.settingsPageModel())
        assertEquals(SettingsPage.Appearance, controller.current().settingsPage)
    }

    @Test
    fun nonUserTabNavigationPreservesNetworkSubpage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            networkPage = NetworkPage.StreamList,
            selectedRemoteSourceId = 42L
        )

        controller.navigateToTab(app.yukine.navigation.NetworkTab, userInitiated = false)

        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(NetworkPage.StreamList, state.networkPage)
        assertEquals(42L, state.selectedRemoteSourceId)
    }

    @Test
    fun networkEntryReturnsToTheSettingsPageThatOpenedIt() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_SOURCES_GROUP
        )

        controller.navigateToNetworkPageFromCurrent(NetworkPage.Home)

        assertEquals(NetworkTab, controller.current().selectedTab)
        assertEquals(NetworkPage.Home, controller.current().networkPage)

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(true, result.navigateTab)
        assertEquals(SettingsTab, controller.current().selectedTab)
        assertEquals(SettingsPage.SourcesGroup, controller.current().settingsPage)
    }

    @Test
    fun nestedNetworkPageReturnsToEntryBeforeRestoringPreviousPage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_SOURCES_GROUP
        )
        controller.navigateToNetworkPageFromCurrent(NetworkPage.Sources)
        controller.setNetworkPage(NetworkPage.WebDavSourceTracks)

        controller.applyBackNavigation()

        assertEquals(NetworkTab, controller.current().selectedTab)
        assertEquals(NetworkPage.Sources, controller.current().networkPage)

        controller.applyBackNavigation()

        assertEquals(SettingsTab, controller.current().selectedTab)
        assertEquals(SettingsPage.SourcesGroup, controller.current().settingsPage)
    }

    @Test
    fun nonUserTabNavigationPreservesSettingsSubpage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            settingsPage = MainRoutes.SETTINGS_APPEARANCE
        )

        controller.navigateToTab(app.yukine.navigation.SettingsTab, userInitiated = false)

        val state = controller.current()
        assertEquals(SettingsTab, state.selectedTab)
        assertEquals(SettingsPage.Appearance, state.settingsPage)
    }

    @Test
    fun nonUserTabNavigationPreservesLibraryDirectory() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_QUEUE,
            libraryMode = LibraryGrouping.PLAYLISTS,
            selectedLibraryGroupKey = "playlist:7",
            selectedLibraryGroupTitle = "Favorites",
            selectedPlaylistId = 7L
        )

        controller.navigateToTab(app.yukine.navigation.LibraryTab, userInitiated = false)

        val state = controller.current()
        assertEquals(LibraryTab, state.selectedTab)
        assertEquals(LibraryPage.Browse, state.libraryPage)
        assertEquals(LibraryGrouping.PLAYLISTS, state.libraryMode)
        assertEquals("playlist:7", state.selectedLibraryGroupKey)
        assertEquals("Favorites", state.selectedLibraryGroupTitle)
        assertEquals(7L, state.selectedPlaylistId)
    }

    @Test
    fun setLibraryModeClearsPreviousLibraryDetailState() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.PLAYLISTS,
            selectedLibraryGroupKey = "playlist:7",
            selectedLibraryGroupTitle = "Favorites",
            selectedPlaylistId = 7L
        )

        controller.setLibraryMode(LibraryGrouping.ALBUMS)

        val state = controller.current()
        assertEquals(LibraryGrouping.ALBUMS, state.libraryMode)
        assertEquals(LibraryPage.Browse, state.libraryPage)
        assertEquals("", state.selectedLibraryGroupKey)
        assertEquals("", state.selectedLibraryGroupTitle)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    @Test
    fun playHistoryRouteSelectsVirtualHistoryInsidePlaylistMode() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.SONGS
        )

        openPlayHistoryRoute(controller, AppLanguage.MODE_CHINESE)

        val state = controller.current()
        assertEquals(LibraryGrouping.PLAYLISTS, state.libraryMode)
        assertEquals(LibraryPage.Browse, state.libraryPage)
        assertEquals(LibraryPlaylistsStateReducer.HISTORY_GROUP_KEY, state.selectedLibraryGroupKey)
        assertEquals(AppLanguage.text(AppLanguage.MODE_CHINESE, "play.history.playlist"), state.selectedLibraryGroupTitle)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    @Test
    fun userTabNavigationStillResetsExplicitRootDestinations() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_HOME,
            networkPage = NetworkPage.StreamList,
            settingsPage = MainRoutes.SETTINGS_APPEARANCE,
            selectedRemoteSourceId = 42L
        )

        controller.navigateToTab(app.yukine.navigation.NetworkTab, userInitiated = true)
        var state = controller.current()
        assertEquals(NetworkPage.Home, state.networkPage)
        assertEquals(-1L, state.selectedRemoteSourceId)

        controller.navigateToTab(app.yukine.navigation.SettingsTab, userInitiated = true)
        state = controller.current()
        assertEquals(SettingsPage.Home, state.settingsPage)
    }

    @Test
    fun openLibraryModeNavigatesDirectlyToBrowseRoot() {
        val controller = controllerWith(selectedTab = MainRoutes.TAB_HOME)

        controller.openLibraryMode(LibraryGrouping.ALBUMS)

        val state = controller.current()
        assertEquals(LibraryTab, state.selectedTab)
        assertEquals(LibraryPage.Browse, state.libraryPage)
        assertEquals(LibraryGrouping.ALBUMS, state.libraryMode)
        assertEquals("", state.selectedLibraryGroupKey)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    @Test
    fun openLibraryPlaylistNavigatesDirectlyToItsTrackList() {
        val controller = controllerWith(selectedTab = MainRoutes.TAB_HOME)

        controller.openLibraryPlaylist(7L, "Favorites")

        val state = controller.current()
        assertEquals(LibraryTab, state.selectedTab)
        assertEquals(LibraryPage.Browse, state.libraryPage)
        assertEquals(LibraryGrouping.PLAYLISTS, state.libraryMode)
        assertEquals("playlist:7", state.selectedLibraryGroupKey)
        assertEquals("Favorites", state.selectedLibraryGroupTitle)
        assertEquals(7L, state.selectedPlaylistId)
    }

    @Test
    fun userLibraryTabNavigationReturnsToOverview() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.ALBUMS,
            selectedLibraryGroupKey = "album:one",
            selectedLibraryGroupTitle = "Album One"
        )

        controller.navigateToTab(LibraryTab, userInitiated = true)

        val state = controller.current()
        assertEquals(LibraryPage.Overview, state.libraryPage)
        assertEquals("", state.selectedLibraryGroupKey)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    @Test
    fun backNavigationReturnsConcreteSettingsPageToItsGroup() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(SettingsPage.SourcesGroup, controller.current().settingsPage)
    }

    @Test
    fun backNavigationReturnsSettingsGroupToSettingsHome() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_PLAYBACK_GROUP
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(SettingsPage.Home, controller.current().settingsPage)
    }

    @Test
    fun backNavigationReturnsAdvancedThemePageToThemePage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_ADVANCED_THEME
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(SettingsPage.Appearance, controller.current().settingsPage)
    }

    @Test
    fun backNavigationUsesTypedSettingsPageState() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_STREAMING_GATEWAY
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(SettingsPage.SourcesGroup, controller.current().settingsPage)
    }

    @Test
    fun backNavigationReturnsStreamingHubToNetworkHome() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_NETWORK,
            networkPage = NetworkPage.StreamingHub,
            selectedRemoteSourceId = 42L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(false, result.navigateTab)
        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(NetworkPage.Home, state.networkPage)
        assertEquals(-1L, state.selectedRemoteSourceId)
    }

    @Test
    fun backNavigationReturnsStreamListToStreamingEntry() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_NETWORK,
            networkPage = NetworkPage.StreamList,
            selectedRemoteSourceId = 42L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(false, result.navigateTab)
        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(NetworkPage.Streaming, state.networkPage)
        assertEquals(42L, state.selectedRemoteSourceId)
    }

    @Test
    fun backNavigationClosesLibraryPlaylistDetailCompletely() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.PLAYLISTS,
            selectedLibraryGroupKey = "playlist:7",
            selectedLibraryGroupTitle = "Favorites",
            selectedPlaylistId = 7L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        val state = controller.current()
        assertEquals("", state.selectedLibraryGroupKey)
        assertEquals("", state.selectedLibraryGroupTitle)
        assertEquals(-1L, state.selectedPlaylistId)
        assertEquals(LibraryPage.Browse, state.libraryPage)
    }

    @Test
    fun backNavigationWalksLibraryDetailThenOverviewThenHome() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.ALBUMS,
            selectedLibraryGroupKey = "album:one",
            selectedLibraryGroupTitle = "Album One"
        )

        controller.applyBackNavigation()
        assertEquals(LibraryPage.Browse, controller.current().libraryPage)
        assertEquals("", controller.current().selectedLibraryGroupKey)
        assertEquals(LibraryTab, controller.current().selectedTab)

        controller.applyBackNavigation()
        assertEquals(LibraryPage.Overview, controller.current().libraryPage)
        assertEquals(LibraryTab, controller.current().selectedTab)

        controller.applyBackNavigation()
        assertEquals(app.yukine.navigation.HomeTab, controller.current().selectedTab)
    }

    @Test
    fun legacySavedLibraryDetailRestoresAsBrowsePage() {
        val viewModel = NavigationViewModel(
            SavedStateHandle(
                mapOf(
                    "selectedTab" to MainRoutes.TAB_LIBRARY,
                    "libraryMode" to LibraryGrouping.ALBUMS,
                    "selectedLibraryGroupKey" to "album:one",
                    "selectedLibraryGroupTitle" to "Album One"
                )
            )
        )

        assertEquals(LibraryPage.Browse, viewModel.state.value.libraryPage)
        assertEquals("album:one", viewModel.state.value.selectedLibraryGroupKey)
    }

    @Test
    fun removedCollectionsRouteRestoresAsLibrary() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_COLLECTIONS,
            selectedPlaylistId = 7L
        )

        val state = controller.current()
        assertEquals(LibraryTab, state.selectedTab)
        assertEquals(7L, state.selectedPlaylistId)
    }

    @Test
    fun backToOverviewClearsSearchQuery() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.SONGS,
            libraryPage = LibraryPage.Browse,
            searchQuery = "test query"
        )

        controller.applyBackNavigation()

        assertEquals(LibraryPage.Overview, controller.current().libraryPage)
        assertEquals("", controller.current().searchQuery)
    }

    @Test
    fun setLibraryModeClearsSearchQuery() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.SONGS,
            libraryPage = LibraryPage.Browse,
            searchQuery = "test query"
        )

        controller.setLibraryMode(LibraryGrouping.ALBUMS)

        assertEquals(LibraryGrouping.ALBUMS, controller.current().libraryMode)
        assertEquals("", controller.current().searchQuery)
    }

    @Test
    fun userInitiatedLibraryTabNavigationClearsSearchQuery() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            libraryMode = LibraryGrouping.SONGS,
            libraryPage = LibraryPage.Browse,
            searchQuery = "test query"
        )

        controller.navigateToTab(LibraryTab, userInitiated = true)

        assertEquals(LibraryPage.Overview, controller.current().libraryPage)
        assertEquals("", controller.current().searchQuery)
    }

    private fun controllerWith(
        selectedTab: String,
        libraryMode: String = LibraryGrouping.SONGS,
        selectedLibraryGroupKey: String = "",
        selectedLibraryGroupTitle: String = "",
        selectedPlaylistId: Long = -1L,
        libraryPage: LibraryPage = if (
            selectedLibraryGroupKey.isNotEmpty() || selectedPlaylistId >= 0L
        ) {
            LibraryPage.Browse
        } else {
            LibraryPage.Overview
        },
        networkPage: NetworkPage = NetworkPage.Home,
        settingsPage: String = MainRoutes.SETTINGS_HOME,
        selectedRemoteSourceId: Long = -1L,
        searchQuery: String = ""
    ): MainRouteController {
        val viewModel = NavigationViewModel(SavedStateHandle())
        val controller = MainRouteController(viewModel)
        controller.persist(
            NavigationRouteState(
                selectedTab = TabRoute.fromKey(selectedTab) ?: error("Unknown tab: $selectedTab"),
                libraryPage = libraryPage,
                libraryMode = libraryMode,
                selectedLibraryGroupKey = selectedLibraryGroupKey,
                selectedLibraryGroupTitle = selectedLibraryGroupTitle,
                selectedPlaylistId = selectedPlaylistId,
                searchQuery = searchQuery,
                networkPage = networkPage,
                settingsPage = SettingsPage.fromRoute(settingsPage),
                selectedRemoteSourceId = selectedRemoteSourceId
            )
        )
        return controller
    }
}
