package app.echo.next.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
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
import app.echo.next.SettingsUiState
import app.echo.next.SettingsViewModel
import app.echo.next.queue.QueueViewModel
import app.echo.next.ui.CollectionsActions
import app.echo.next.ui.CollectionsUiState
import app.echo.next.ui.EchoTheme
import app.echo.next.ui.HomeDashboardActions
import app.echo.next.ui.HomeDashboardUiState
import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsMetric
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

    private fun emptyQueueViewModel(): QueueViewModel =
        QueueViewModel().also { it.bind(emptyList(), null, emptySet(), "en") }

    private fun hostState(): EchoNavHostState {
        val main = MainActivityViewModel(SavedStateHandle())
        main.updateHomeDashboard(
            MainActivityHomeDashboardUiState(
                HomeDashboardUiState(heroTitle = "Native home")
            ).content
        )
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
                    selectedPlaylistTracks = emptyList()
                )
            )
        }
        val settings = SettingsViewModel().also {
            it.updatePage(
                "Native settings",
                listOf(SettingsMetric("Metric", "1")),
                listOf(SettingsAction("Appearance", Runnable {}))
            )
        }
        return EchoNavHostState(
            mainViewModel = main,
            nowPlayingViewModel = NowPlayingViewModel(),
            libraryViewModel = LibraryViewModel(),
            collectionsViewModel = collections,
            settingsViewModel = settings,
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
            ),
            settingsActions = listOf(SettingsAction("Appearance", Runnable {}))
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

        composeRule.onNodeWithText("Native home").assertIsDisplayed()
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
    fun selectingSettingsTab_rendersNativeSettingsWhenHostStateIsProvided() {
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

        composeRule.onNodeWithText("Native settings").assertIsDisplayed()
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
}
