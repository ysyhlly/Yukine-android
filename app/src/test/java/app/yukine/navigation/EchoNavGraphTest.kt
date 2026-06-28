package app.yukine.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import app.yukine.CollectionsViewModel
import app.yukine.HomeDashboardViewModel
import app.yukine.LibraryViewModel
import app.yukine.MainActivityViewModel
import app.yukine.model.Track
import app.yukine.NavigationViewModel
import app.yukine.NetworkMenuViewModel
import app.yukine.NetworkSourcesViewModel
import app.yukine.NowPlayingViewModel
import app.yukine.SettingsUiState
import app.yukine.SettingsViewModel
import app.yukine.StreamingViewModel
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.queue.QueueViewModel
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.emptyCollectionsActions
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardUiState
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EchoNavGraphTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(
        EchoTabItem(HomeTab, "Home"),
        EchoTabItem(LibraryTab, "Library"),
        EchoTabItem(QueueTab, "Playing"),
        EchoTabItem(SettingsTab, "Settings")
    )
    private val emptyRealtimeBands = FloatArray(0)

    private fun emptyQueueViewModel(): QueueViewModel =
        QueueViewModel().also { it.bind(emptyList(), null, emptySet(), "en") }

    private fun hostState(
        visualMotionEnabled: Boolean = false,
        realtimeBeatProvider: () -> Float = { 0f },
        realtimeBandsProvider: () -> FloatArray = { emptyRealtimeBands },
        nowPlayingStateProvider: NowPlayingViewModel = NowPlayingViewModel()
    ): EchoNavHostState {
        val homeDashboard = HomeDashboardViewModel(null).also {
            it.updateHomeDashboard(
                HomeDashboardUiState(heroTitle = "Native home")
            )
        }
        val collections = CollectionsViewModel().also {
            it.updateScreen(
                CollectionsUiState(
                    title = "Native collections",
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
        val settings = SettingsViewModel().also {
            it.renderPageFromHost(
                app.yukine.SettingsPage.Home,
                app.yukine.SettingsPreferencesSnapshot(),
                app.yukine.RuntimeSettingsStatus()
            )
        }
        return EchoNavHostState(
            mainViewModel = MainActivityViewModel(SavedStateHandle()),
            navigationViewModel = NavigationViewModel(SavedStateHandle()),
            homeDashboardViewModel = homeDashboard,
            nowPlayingStateProvider = nowPlayingStateProvider,
            libraryViewModel = LibraryViewModel(),
            collectionsViewModel = collections,
            settingsViewModel = settings,
            networkMenuViewModel = NetworkMenuViewModel(),
            networkSourcesViewModel = NetworkSourcesViewModel(),
            streamingViewModel = StreamingViewModel(),
            playbackViewModel = app.yukine.PlaybackViewModel(),
            realtimeBeatProvider = realtimeBeatProvider,
            realtimeBandsProvider = realtimeBandsProvider,
            visualMotionEnabled = visualMotionEnabled
        )
    }

    @Test
    fun startDestination_rendersNativeHome() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    hostState = hostState()
                )
            }
        }

        composeRule.onAllNodesWithText("Native home").assertCountEquals(1)
    }

    @Test
    fun stoppedPlayback_doesNotPollRealtimeVisualProviders() {
        composeRule.mainClock.autoAdvance = false
        try {
            var beatPolls = 0
            var bandPolls = 0
            val state = hostState(
                visualMotionEnabled = true,
                realtimeBeatProvider = {
                    beatPolls += 1
                    0.5f
                },
                realtimeBandsProvider = {
                    bandPolls += 1
                    floatArrayOf(0.2f)
                }
            )
            composeRule.setContent {
                EchoTheme.EchoTheme {
                    EchoNavGraph(
                        tabs = tabs,
                        queueViewModel = emptyQueueViewModel(),
                        hostState = state
                    )
                }
            }

            composeRule.mainClock.advanceTimeBy(500)

            assertEquals(0, beatPolls)
            assertEquals(0, bandPolls)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun selectingPlayingTab_updatesHostStateRoute() {
        val state = hostState()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    hostState = state
                )
            }
        }

        composeRule.onNode(hasContentDescription("Playing") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(QueueTab.route, state.selectedTabRoute)
    }

    @Test
    fun clickingNowBarTrack_opensImmersiveNowPlayingRoute() {
        val nowPlayingViewModel = NowPlayingViewModel()
        val state = hostState(nowPlayingStateProvider = nowPlayingViewModel)
        nowPlayingViewModel.updateState(nowPlayingSnapshot(), emptySet(), null)
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    hostState = state
                )
            }
        }

        composeRule.onNodeWithText("Song").performClick()
        composeRule.waitForIdle()

        assertEquals(QueueTab.route, state.selectedTabRoute)
        composeRule.onAllNodesWithText("Elapsed").assertCountEquals(0)
    }

    @Test
    fun selectingSettingsTab_rendersSettingsHomeWhenHostStateIsProvided() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    hostState = hostState()
                )
            }
        }

        composeRule.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun tabChange_emitsOnTabChangedCallback() {
        val changed = mutableListOf<TabRoute>()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    onTabChanged = { changed.add(it) },
                    hostState = hostState()
                )
            }
        }

        composeRule.onNode(hasContentDescription("Library") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(1, changed.size)
        assertEquals(LibraryTab.route, changed.first().route)
    }

    @Test
    fun nowBarSlot_persistsAcrossTabs() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    queueViewModel = emptyQueueViewModel(),
                    nowBar = { Text("persistent-now-bar") },
                    hostState = hostState()
                )
            }
        }

        composeRule.onNodeWithText("persistent-now-bar").assertExists()
        composeRule.onNode(hasContentDescription("Playing") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("persistent-now-bar").assertExists()
    }

    private fun nowPlayingSnapshot(): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            Track(7L, "Song", "Artist", "Album", 180_000L, android.net.Uri.EMPTY, "file:song.mp3"),
            0,
            1,
            0L,
            180_000L,
            true,
            false,
            "",
            false,
            EchoPlaybackService.REPEAT_ALL,
            1.0f,
            1.0f,
            0L
        )
}
