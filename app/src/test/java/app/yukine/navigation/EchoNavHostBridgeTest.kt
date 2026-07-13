package app.yukine.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import app.yukine.CollectionsViewModel
import app.yukine.HomeDashboardViewModel
import app.yukine.LibraryViewModel
import app.yukine.NavigationViewModel
import app.yukine.NetworkMenuViewModel
import app.yukine.NetworkSourcesViewModel
import app.yukine.NowPlayingViewModel
import app.yukine.SettingsViewModel
import app.yukine.StreamingViewModel
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.emptyCollectionsActions
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EchoNavHostBridgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(
        EchoTabItem(HomeTab, "Home"),
        EchoTabItem(QueueTab, "Playing"),
        EchoTabItem(SettingsTab, "Settings")
    )

    private fun hostState(
        navigationViewModel: NavigationViewModel = NavigationViewModel(SavedStateHandle())
    ): EchoNavHostState {
        val homeDashboard = HomeDashboardViewModel(null).also {
            it.updateHomeDashboard(
                HomeDashboardUiState(heroTitle = "Bridge native home")
            )
        }
        val collections = CollectionsViewModel().also {
            it.updateScreen(
                CollectionsUiState(
                    title = "",
                    metrics = emptyList(),
                    topActions = emptyList(),
                    trackSections = emptyList(),
                    playlistTitle = "",
                    playlistEmptyText = "",
                    playlistEmptyDescription = "",
                    playlists = emptyList(),
                    selectedPlaylistVisible = false,
                    selectedPlaylistTitle = "",
                    selectedPlaylistEmptyText = "",
                    selectedPlaylistEmptyDescription = "",
                    selectedPlaylistTopActions = emptyList(),
                    selectedPlaylistTracks = emptyList(),
                    actions = emptyCollectionsActions()
                )
            )
        }
        val library = LibraryViewModel()
        val networkMenu = NetworkMenuViewModel()
        val networkSources = NetworkSourcesViewModel()
        val streaming = StreamingViewModel()
        val settings = SettingsViewModel()
        return EchoNavHostState(
            routeState = navigationViewModel.state,
            homeDashboardState = homeDashboard.uiState,
            nowPlayingStateProvider = NowPlayingViewModel(),
            libraryGroupsState = library.libraryGroups,
            libraryTrackListState = library.trackList,
            collectionsStateProvider = collections,
            settingsState = settings.state,
            settingsChromeState = settings.chromeState,
            settingsScrollState = settings.scrollState,
            networkMenuState = networkMenu.uiState,
            networkSourcesState = networkSources.uiState,
            streamingState = streaming.streaming,
            playbackSnapshotProvider = app.yukine.PlaybackViewModel(),
            visualMotionEnabled = false
        )
    }

    @Test
    fun bridgeMountsNativeHomeWithoutViewFactories() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    hostState = hostState()
                )
            }
        }

        composeRule.onAllNodesWithText("Bridge native home").assertCountEquals(1)
    }

    @Test
    fun queueTab_updatesHostStateRoute() {
        val navigationViewModel = NavigationViewModel(SavedStateHandle())
        val state = hostState(navigationViewModel)
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    hostState = state,
                    onTabChanged = { tab ->
                        navigationViewModel.updateRoute(
                            navigationViewModel.state.value.copy(selectedTab = tab)
                        )
                    }
                )
            }
        }

        composeRule.onNode(hasContentDescription("Playing") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(QueueTab, navigationViewModel.state.value.selectedTab)
    }

    @Test
    fun tabChange_emitsCallback() {
        val changed = mutableListOf<TabRoute>()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    hostState = hostState(),
                    onTabChanged = { changed.add(it) }
                )
            }
        }

        composeRule.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(1, changed.size)
        assertEquals(SettingsTab.route, changed.first().route)
    }
}
