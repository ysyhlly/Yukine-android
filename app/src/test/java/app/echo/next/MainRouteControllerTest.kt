package app.echo.next

import androidx.lifecycle.SavedStateHandle
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
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val controller = MainRouteController(viewModel)
        controller.persist(
            MainActivityRouteState(
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
