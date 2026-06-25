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
import app.yukine.MainActivityViewModel
import app.yukine.NavigationViewModel
import app.yukine.NetworkMenuViewModel
import app.yukine.NetworkSourcesViewModel
import app.yukine.NowPlayingViewModel
import app.yukine.SettingsViewModel
import app.yukine.StreamingViewModel
import app.yukine.queue.QueueViewModel
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

    private fun emptyQueueVm() = QueueViewModel().also { it.bind(emptyList(), null, emptySet(), "en") }

    private fun hostState(): EchoNavHostState {
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
        return EchoNavHostState(
            mainViewModel = MainActivityViewModel(SavedStateHandle()),
            navigationViewModel = NavigationViewModel(SavedStateHandle()),
            homeDashboardViewModel = homeDashboard,
            nowPlayingViewModel = NowPlayingViewModel(),
            libraryViewModel = LibraryViewModel(),
            collectionsViewModel = collections,
            settingsViewModel = SettingsViewModel(),
            networkMenuViewModel = NetworkMenuViewModel(),
            networkSourcesViewModel = NetworkSourcesViewModel(),
            streamingViewModel = StreamingViewModel(),
            playbackViewModel = app.yukine.PlaybackViewModel(),
            visualMotionEnabled = false
        )
    }

    @Test
    fun bridgeMountsNativeHomeWithoutViewFactories() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    queueViewModel = emptyQueueVm(),
                    hostState = hostState()
                )
            }
        }

        composeRule.onAllNodesWithText("Bridge native home").assertCountEquals(1)
    }

    @Test
    fun queueTab_updatesHostStateRoute() {
        val state = hostState()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    queueViewModel = emptyQueueVm(),
                    hostState = state
                )
            }
        }

        composeRule.onNode(hasContentDescription("Playing") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(QueueTab.route, state.selectedTabRoute)
    }

    @Test
    fun tabChange_emitsCallback() {
        val changed = mutableListOf<TabRoute>()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavHostBridge(
                    tabs = tabs,
                    queueViewModel = emptyQueueVm(),
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
