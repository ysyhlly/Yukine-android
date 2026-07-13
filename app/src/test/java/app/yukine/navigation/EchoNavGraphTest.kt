package app.yukine.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import app.yukine.CollectionsViewModel
import app.yukine.HomeDashboardViewModel
import app.yukine.LibraryViewModel
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
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.emptyCollectionsActions
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardUiState
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import app.yukine.ui.SeekAction
import app.yukine.ui.nowBarEmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun hostState(
        visualMotionEnabled: Boolean = false,
        realtimeBeatProvider: () -> Float = { 0f },
        realtimeBandsProvider: () -> FloatArray = { emptyRealtimeBands },
        nowPlayingStateProvider: NowPlayingViewModel = NowPlayingViewModel(),
        playbackSnapshotProvider: PlaybackSnapshotProvider = app.yukine.PlaybackViewModel(),
        navigationViewModel: NavigationViewModel = NavigationViewModel(SavedStateHandle()),
        queueSheetVisibilityListener: QueueSheetVisibilityListener = QueueSheetVisibilityListener { }
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
        val library = LibraryViewModel()
        val networkMenu = NetworkMenuViewModel()
        val networkSources = NetworkSourcesViewModel()
        val streaming = StreamingViewModel()
        val settings = SettingsViewModel().also {
            it.renderCurrentPage(
                app.yukine.SettingsPage.Home,
                app.yukine.SettingsPreferencesSnapshot(),
                app.yukine.RuntimeSettingsStatus()
            )
        }
        return EchoNavHostState(
            routeState = navigationViewModel.state,
            homeDashboardState = homeDashboard.uiState,
            nowPlayingStateProvider = nowPlayingStateProvider,
            libraryGroupsState = library.libraryGroups,
            libraryTrackListState = library.trackList,
            collectionsStateProvider = collections,
            settingsState = settings.state,
            settingsChromeState = settings.chromeState,
            settingsScrollState = settings.scrollState,
            networkMenuState = networkMenu.uiState,
            networkSourcesState = networkSources.uiState,
            streamingState = streaming.streaming,
            playbackSnapshotProvider = playbackSnapshotProvider,
            realtimeBeatProvider = realtimeBeatProvider,
            realtimeBandsProvider = realtimeBandsProvider,
            visualMotionEnabled = visualMotionEnabled,
            queueSheetVisibilityListener = queueSheetVisibilityListener
        )
    }

    @Test
    fun startDestination_rendersNativeHome() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
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
    fun playingHomeTabPollsRealtimeVisualProvidersForTheVisibleOrb() {
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
                },
                playbackSnapshotProvider = object : PlaybackSnapshotProvider {
                    override val playbackSnapshot = MutableStateFlow(nowPlayingSnapshot())
                }
            )
            composeRule.setContent {
                EchoTheme.EchoTheme {
                    EchoNavGraph(
                        tabs = tabs,
                        hostState = state
                    )
                }
            }

            composeRule.mainClock.advanceTimeBy(50)

            assertTrue(beatPolls > 0)
            assertTrue(bandPolls > 0)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun selectingPlayingTab_updatesHostStateRoute() {
        val navigationViewModel = NavigationViewModel(SavedStateHandle())
        val state = hostState(navigationViewModel = navigationViewModel)
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
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
    fun clickingNowBarTrack_opensImmersiveNowPlayingRoute() {
        val nowPlayingViewModel = NowPlayingViewModel()
        val navigationViewModel = NavigationViewModel(SavedStateHandle())
        val state = hostState(
            nowPlayingStateProvider = nowPlayingViewModel,
            navigationViewModel = navigationViewModel
        )
        nowPlayingViewModel.updateState(nowPlayingSnapshot(), emptySet(), null)
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
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

        composeRule.onNodeWithText("Song").performClick()
        composeRule.waitForIdle()

        assertEquals(QueueTab, navigationViewModel.state.value.selectedTab)
        composeRule.onAllNodesWithText("Elapsed").assertCountEquals(0)
    }

    @Test
    fun clickingNowBarQueueMakesQueueSheetVisibleToHost() {
        val visibilityChanges = mutableListOf<Boolean>()
        val nowPlayingViewModel = NowPlayingViewModel()
        val state = hostState(
            nowPlayingStateProvider = nowPlayingViewModel,
            queueSheetVisibilityListener = QueueSheetVisibilityListener { visible ->
                visibilityChanges += visible
            }
        )
        nowPlayingViewModel.updateState(
            nowPlayingSnapshot(),
            emptySet(),
            null
        )
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    hostState = state
                )
            }
        }

        composeRule.onNode(hasContentDescription("Queue") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(true), visibilityChanges)
        assertTrue(state.queueSheetVisible)
    }

    @Test
    fun clickingNowBarProgress_expandsWaveform_andTrackChangeCollapsesIt() {
        var nowBarState by mutableStateOf(waveformNowBarState(1L))
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNowBar(
                    state = nowBarState,
                    onOpenNowPlaying = { },
                    onOpenQueue = { },
                    onPrevious = Runnable { },
                    onPlayPause = Runnable { },
                    onNext = Runnable { },
                    onFavorite = Runnable { },
                    onShuffle = Runnable { },
                    onRepeat = Runnable { },
                    onSeek = SeekAction { _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("expand-waveform").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("waveform-progress").assertIsDisplayed()

        nowBarState = waveformNowBarState(2L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("waveform-progress").assertCountEquals(0)
    }

    @Test
    fun selectingSettingsTab_rendersSettingsHomeWhenHostStateIsProvided() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
                    hostState = hostState()
                )
            }
        }

        composeRule.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        // Assert the first settings group: later groups are lazily composed and can sit outside
        // Robolectric's compact viewport.
        composeRule.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun tabChange_emitsOnTabChangedCallback() {
        val changed = mutableListOf<TabRoute>()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoNavGraph(
                    tabs = tabs,
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

    private fun waveformNowBarState(trackId: Long) = nowBarEmptyState().copy(
        title = "Song",
        canExpand = true,
        duration = "3:00",
        durationMs = 180_000L,
        trackId = trackId,
        dataPath = "file:waveform-$trackId.mp3",
        playbackProgressLabel = "waveform-progress",
        expandWaveformLabel = "expand-waveform"
    )
}
