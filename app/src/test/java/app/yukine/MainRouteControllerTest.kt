package app.yukine

import androidx.lifecycle.SavedStateHandle
import app.yukine.NavigationViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRouteControllerTest {
    @Test
    fun nonUserTabNavigationPreservesNetworkSubpage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            networkPage = MainRoutes.NETWORK_STREAM_LIST,
            selectedRemoteSourceId = 42L
        )

        controller.navigateToTab(MainRoutes.TAB_NETWORK, userInitiated = false)

        val state = controller.current()
        assertEquals(MainRoutes.TAB_NETWORK, state.selectedTab)
        assertEquals(MainRoutes.NETWORK_STREAM_LIST, state.networkPage)
        assertEquals(42L, state.selectedRemoteSourceId)
    }

    @Test
    fun nonUserTabNavigationPreservesSettingsSubpage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_LIBRARY,
            settingsPage = MainRoutes.SETTINGS_APPEARANCE
        )

        controller.navigateToTab(MainRoutes.TAB_SETTINGS, userInitiated = false)

        val state = controller.current()
        assertEquals(MainRoutes.TAB_SETTINGS, state.selectedTab)
        assertEquals(MainRoutes.SETTINGS_APPEARANCE, state.settingsPage)
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

        controller.navigateToTab(MainRoutes.TAB_LIBRARY, userInitiated = false)

        val state = controller.current()
        assertEquals(MainRoutes.TAB_LIBRARY, state.selectedTab)
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

        controller.navigateToTab(MainRoutes.TAB_NETWORK, userInitiated = true)
        var state = controller.current()
        assertEquals(MainRoutes.NETWORK_HOME, state.networkPage)
        assertEquals(-1L, state.selectedRemoteSourceId)

        controller.navigateToTab(MainRoutes.TAB_SETTINGS, userInitiated = true)
        state = controller.current()
        assertEquals(MainRoutes.SETTINGS_HOME, state.settingsPage)
    }

    @Test
    fun backNavigationReturnsConcreteSettingsPageToItsGroup() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(MainRoutes.SETTINGS_SOURCES_GROUP, controller.current().settingsPage)
    }

    @Test
    fun backNavigationReturnsSettingsGroupToSettingsHome() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_PLAYBACK_GROUP
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(MainRoutes.SETTINGS_HOME, controller.current().settingsPage)
    }

    @Test
    fun backNavigationReturnsAdvancedThemePageToThemePage() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_ADVANCED_THEME
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(MainRoutes.SETTINGS_APPEARANCE, controller.current().settingsPage)
    }

    @Test
    fun backNavigationUsesTypedSettingsPageState() {
        val controller = controllerWith(
            selectedTab = MainRoutes.TAB_SETTINGS,
            settingsPage = MainRoutes.SETTINGS_STREAMING_GATEWAY
        )

        val result = controller.applyBackNavigation()

        assertEquals(true, result.handled)
        assertEquals(MainRoutes.SETTINGS_SOURCES_GROUP, controller.current().settingsPage)
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
        assertEquals(MainRoutes.TAB_NETWORK, state.selectedTab)
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
        assertEquals(MainRoutes.TAB_NETWORK, state.selectedTab)
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
        assertEquals(MainRoutes.TAB_COLLECTIONS, state.selectedTab)
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
                selectedTab = selectedTab,
                libraryMode = libraryMode,
                selectedLibraryGroupKey = selectedLibraryGroupKey,
                selectedLibraryGroupTitle = selectedLibraryGroupTitle,
                selectedPlaylistId = selectedPlaylistId,
                networkPage = networkPage,
                settingsPage = settingsPage,
                selectedRemoteSourceId = selectedRemoteSourceId
            )
        )
        return controller
    }
}
