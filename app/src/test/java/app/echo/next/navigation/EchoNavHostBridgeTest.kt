package app.echo.next.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import app.echo.next.CollectionsViewModel
import app.echo.next.LibraryViewModel
import app.echo.next.MainActivityHomeDashboardUiState
import app.echo.next.MainActivityViewModel
import app.echo.next.NetworkSourcesViewModel
import app.echo.next.NowPlayingViewModel
import app.echo.next.SettingsViewModel
import app.echo.next.queue.QueueViewModel
import app.echo.next.ui.CollectionsActions
import app.echo.next.ui.CollectionsUiState
import app.echo.next.ui.EchoTheme
import app.echo.next.ui.HomeDashboardActions
import app.echo.next.ui.HomeDashboardUiState
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
        val main = MainActivityViewModel(SavedStateHandle())
        main.updateHomeDashboard(
            MainActivityHomeDashboardUiState(
                HomeDashboardUiState(heroTitle = "Bridge native home")
            ).content
        )
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
                    selectedPlaylistTracks = emptyList()
                )
            )
        }
        return EchoNavHostState(
            mainViewModel = main,
            nowPlayingViewModel = NowPlayingViewModel(),
            libraryViewModel = LibraryViewModel(),
            collectionsViewModel = collections,
            settingsViewModel = SettingsViewModel(),
            networkSourcesViewModel = NetworkSourcesViewModel(),
            homeActions = HomeDashboardActions(
                onOpenStat = emptyList(),
                onContinue = Runnable {},
                onOpenNowPlaying = Runnable {},
                onPlayRecent = emptyList(),
                onRefresh = Runnable {},
                onViewQueue = Runnable {},
                onShuffleAll = Runnable {},
                onRecentTabChanged = {}
            ),
            collectionsActions = CollectionsActions(
                onBack = null,
                topActions = emptyList(),
                trackSections = emptyList(),
                playlistActions = emptyList(),
                selectedPlaylistTopActions = emptyList(),
                selectedPlaylistTrackActions = emptyList()
            )
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

        composeRule.onNodeWithText("Bridge native home").assertIsDisplayed()
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
