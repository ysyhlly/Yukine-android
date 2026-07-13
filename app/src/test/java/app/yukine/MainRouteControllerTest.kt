package app.yukine

import androidx.lifecycle.SavedStateHandle
import app.yukine.navigation.CollectionsTab
import app.yukine.navigation.LibraryTab
import app.yukine.navigation.NetworkTab
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.TabRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRouteControllerTest {
    @Test
    fun savedStateKeepsLegacyStringsAtSerializationBoundaryOnly() {
        val handle = SavedStateHandle(
            mapOf(
                "selectedTab" to MainRoutes.TAB_SETTINGS,
                "settingsPage" to MainRoutes.SETTINGS_APPEARANCE
            )
        )
        val viewModel = NavigationViewModel(handle)

        assertEquals(SettingsTab, viewModel.state.value.selectedTab)
        assertEquals(SettingsPage.Appearance, viewModel.state.value.settingsPage)

        viewModel.updateRoute(
            viewModel.state.value.copy(
                selectedTab = NetworkTab,
                settingsPage = SettingsPage.SourcesGroup
            )
        )

        assertEquals(MainRoutes.TAB_NETWORK, handle.get<String>("selectedTab"))
        assertEquals(MainRoutes.SETTINGS_SOURCES_GROUP, handle.get<String>("settingsPage"))
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
            networkPage = MainRoutes.NETWORK_STREAM_LIST,
            selectedRemoteSourceId = 42L
        )

        controller.navigateToTab(app.yukine.navigation.NetworkTab, userInitiated = false)

        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(MainRoutes.NETWORK_STREAM_LIST, state.networkPage)
        assertEquals(42L, state.selectedRemoteSourceId)
    }

    @Test
    fun networkEntryReturnsToTheSettingsPageThatOpenedIt() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_SOURCES_GROUP
        )

        controller.navigateToNetworkPageFromCurrent(MainRoutes.NETWORK_HOME)

        assertEquals(NetworkTab, controller.current().selectedTab)
        assertEquals(MainRoutes.NETWORK_HOME, controller.current().networkPage)

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
        controller.navigateToNetworkPageFromCurrent(MainRoutes.NETWORK_SOURCES)
        controller.setNetworkPage(MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS)

        controller.applyBackNavigation()

        assertEquals(NetworkTab, controller.current().selectedTab)
        assertEquals(MainRoutes.NETWORK_SOURCES, controller.current().networkPage)

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
        assertEquals("", state.selectedLibraryGroupKey)
        assertEquals("", state.selectedLibraryGroupTitle)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    @Test
    fun userTabNavigationStillResetsExplicitRootDestinations() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_HOME,
            networkPage = MainRoutes.NETWORK_STREAM_LIST,
            settingsPage = MainRoutes.SETTINGS_APPEARANCE,
            selectedRemoteSourceId = 42L
        )

        controller.navigateToTab(app.yukine.navigation.NetworkTab, userInitiated = true)
        var state = controller.current()
        assertEquals(MainRoutes.NETWORK_HOME, state.networkPage)
        assertEquals(-1L, state.selectedRemoteSourceId)

        controller.navigateToTab(app.yukine.navigation.SettingsTab, userInitiated = true)
        state = controller.current()
        assertEquals(SettingsPage.Home, state.settingsPage)
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
            networkPage = MainRoutes.NETWORK_STREAMING_HUB,
            selectedRemoteSourceId = 42L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(false, result.navigateTab)
        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(MainRoutes.NETWORK_HOME, state.networkPage)
        assertEquals(-1L, state.selectedRemoteSourceId)
    }

    @Test
    fun backNavigationReturnsStreamListToStreamingEntry() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_NETWORK,
            networkPage = MainRoutes.NETWORK_STREAM_LIST,
            selectedRemoteSourceId = 42L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(false, result.navigateTab)
        val state = controller.current()
        assertEquals(NetworkTab, state.selectedTab)
        assertEquals(MainRoutes.NETWORK_STREAMING, state.networkPage)
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
    }

    @Test
    fun backNavigationClosesSelectedCollectionsPlaylist() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_COLLECTIONS,
            selectedPlaylistId = 7L
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        val state = controller.current()
        assertEquals(CollectionsTab, state.selectedTab)
        assertEquals(-1L, state.selectedPlaylistId)
    }

    private fun controllerWith(
        selectedTab: String,
        libraryMode: String = LibraryGrouping.SONGS,
        selectedLibraryGroupKey: String = "",
        selectedLibraryGroupTitle: String = "",
        selectedPlaylistId: Long = -1L,
        networkPage: String = MainRoutes.NETWORK_HOME,
        settingsPage: String = MainRoutes.SETTINGS_HOME,
        selectedRemoteSourceId: Long = -1L
    ): MainRouteController {
        val viewModel = NavigationViewModel(SavedStateHandle())
        val controller = MainRouteController(viewModel)
        controller.persist(
            NavigationRouteState(
                selectedTab = TabRoute.fromKey(selectedTab) ?: error("Unknown tab: $selectedTab"),
                libraryMode = libraryMode,
                selectedLibraryGroupKey = selectedLibraryGroupKey,
                selectedLibraryGroupTitle = selectedLibraryGroupTitle,
                selectedPlaylistId = selectedPlaylistId,
                networkPage = networkPage,
                settingsPage = SettingsPage.fromRoute(settingsPage),
                selectedRemoteSourceId = selectedRemoteSourceId
            )
        )
        return controller
    }
}
