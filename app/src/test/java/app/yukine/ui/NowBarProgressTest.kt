package app.yukine.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.yukine.playback.PlaybackRepeatMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NowBarProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedProgressDragSeeksWithoutExpandingWaveform() {
        val seekPositions = mutableListOf<Long>()
        var waveformExpanded = false

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowBar(
                    state = progressState(),
                    waveformExpanded = false,
                    onExpandWaveform = { waveformExpanded = true },
                    onCollapseWaveform = { },
                    onPrevious = Runnable { },
                    onPlayPause = Runnable { },
                    onNext = Runnable { },
                    onFavorite = Runnable { },
                    onShuffle = Runnable { },
                    onRepeat = Runnable { },
                    onOpenNowPlaying = Runnable { },
                    onOpenQueue = Runnable { },
                    onSeek = SeekAction { position -> seekPositions += position }
                )
            }
        }

        // Drag must only commit once on finger-up (not during move, not cancelled mid-drag).
        composeRule.onNodeWithContentDescription("Playback progress").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.2f, y))
            moveTo(Offset(visibleSize.width * 0.5f, y))
            assertTrue("seek must not fire while dragging", seekPositions.isEmpty())
            moveTo(Offset(visibleSize.width * 0.8f, y))
            assertTrue(seekPositions.isEmpty())
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, seekPositions.size)
            assertTrue(seekPositions.last() >= 75_000L)
            assertFalse(waveformExpanded)
        }
    }

    @Test
    fun trackIdentityResetsSmoothPositionAndClearsScrubOverlay() {
        var trackId by mutableStateOf(1L)
        var positionMs by mutableStateOf(40_000L)
        val displayHolder = arrayOfNulls<androidx.compose.runtime.State<Long>>(1)
        val scrubHolder = arrayOfNulls<ScrubbablePlaybackPosition>(1)

        composeRule.setContent {
            EchoTheme.EchoTheme {
                val scrub = rememberScrubbablePlaybackPosition(
                    positionMs = positionMs,
                    durationMs = 100_000L,
                    playing = false,
                    trackId = trackId,
                    contentUriString = "content://track/$trackId",
                    dataPath = "/path/$trackId"
                )
                scrubHolder[0] = scrub
                displayHolder[0] = scrub.displayPosition
            }
        }

        composeRule.runOnIdle {
            assertEquals(40_000L, displayHolder[0]!!.value)
            val scrub = scrubHolder[0]!!
            assertEquals(90_000L, scrub.scrubTo(90f, 100f))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(displayHolder[0]!!.value >= 80_000L)
        }

        // Same position/duration/playing but new identity must drop scrub and reseed.
        composeRule.runOnIdle {
            trackId = 2L
            positionMs = 5_000L
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(5_000L, displayHolder[0]!!.value)
        }
    }

    @Test
    fun timeRowRemainsTheWaveformExpansionEntry() {
        var waveformExpanded = false

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowBar(
                    state = progressState(),
                    waveformExpanded = false,
                    onExpandWaveform = { waveformExpanded = true },
                    onCollapseWaveform = { },
                    onPrevious = Runnable { },
                    onPlayPause = Runnable { },
                    onNext = Runnable { },
                    onFavorite = Runnable { },
                    onShuffle = Runnable { },
                    onRepeat = Runnable { },
                    onOpenNowPlaying = Runnable { },
                    onOpenQueue = Runnable { },
                    onSeek = SeekAction { }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Expand waveform").performClick()

        composeRule.runOnIdle { assertTrue(waveformExpanded) }
    }

    @Test
    fun expandedWaveformMatchesReservedProgressHeight() {
        composeRule.setContent {
            var waveformExpanded by remember { mutableStateOf(false) }

            EchoTheme.EchoTheme {
                NowBar(
                    state = progressState().let { state ->
                        state.copy(
                            modes = state.modes.copy(
                                favoriteEnabled = true,
                                repeatMode = PlaybackRepeatMode.REPEAT_OFF
                            ),
                            labels = state.labels.copy(
                                favorite = "Favorite",
                                inOrder = "In order",
                                repeatOff = "Repeat off",
                                queue = "Queue"
                            )
                        )
                    },
                    waveformExpanded = waveformExpanded,
                    onExpandWaveform = { waveformExpanded = true },
                    onCollapseWaveform = { waveformExpanded = false },
                    onPrevious = Runnable { },
                    onPlayPause = Runnable { },
                    onNext = Runnable { },
                    onFavorite = Runnable { },
                    onShuffle = Runnable { },
                    onRepeat = Runnable { },
                    onOpenNowPlaying = Runnable { },
                    onOpenQueue = Runnable { },
                    onSeek = SeekAction { }
                )
            }
        }

        val collapsedProgressBounds = composeRule
            .onNodeWithContentDescription("Playback progress")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithContentDescription("Expand waveform").performClick()
        composeRule.waitForIdle()

        val expandedWaveform = composeRule
            .onNodeWithTag("waveform-progress")
            .assertHeightIsEqualTo(EchoMobileLayoutMetrics.nowBarProgressHeight)
            .fetchSemanticsNode()
            .boundsInRoot
        val trackBounds = composeRule
            .onNodeWithText("Track")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(collapsedProgressBounds.top, expandedWaveform.top, 0.5f)
        assertEquals(collapsedProgressBounds.bottom, expandedWaveform.bottom, 0.5f)
        assertTrue(expandedWaveform.bottom <= trackBounds.top)
        composeRule.onNodeWithText("Favorite").assertIsDisplayed()
        composeRule.onNodeWithText("In order").assertIsDisplayed()
        composeRule.onNodeWithText("Repeat off").assertIsDisplayed()
        composeRule.onNodeWithText("Queue").assertIsDisplayed()
    }

    @Test
    fun scrollCompactedNowBarLocksToCompactHeightAndKeepsPlaybackControls() {
        var playClicks = 0
        var openNowPlayingClicks = 0

        composeRule.setContent {
            EchoTheme.EchoTheme {
                CompositionLocalProvider(LocalEchoNowBarCompactProgress provides 1f) {
                    NowBar(
                        state = progressState().let { state ->
                            state.copy(labels = state.labels.copy(play = "Play"))
                        },
                        waveformExpanded = false,
                        onExpandWaveform = { },
                        onCollapseWaveform = { },
                        onPrevious = Runnable { },
                        onPlayPause = Runnable { playClicks += 1 },
                        onNext = Runnable { },
                        onFavorite = Runnable { },
                        onShuffle = Runnable { },
                        onRepeat = Runnable { },
                        onOpenNowPlaying = Runnable { openNowPlayingClicks += 1 },
                        onOpenQueue = Runnable { },
                        onSeek = SeekAction { }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Height-compact clips progress / mode rows to zero height while keeping their clocks alive.
        val compactedProgressHeight = composeRule
            .onNodeWithContentDescription("Playback progress")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertEquals(0f, compactedProgressHeight, 0.5f)
        composeRule.onNodeWithTag("echo-now-bar-surface")
            .assertHeightIsEqualTo(EchoMobileLayoutMetrics.nowBarCompactHeight)
        composeRule.onNodeWithContentDescription("Play").performClick()
        // Tap title while locked expands instead of opening now-playing.
        composeRule.onNodeWithText("Track").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, playClicks)
            assertEquals(0, openNowPlayingClicks)
        }
        composeRule.onNodeWithContentDescription("Playback progress").assertIsDisplayed()
        composeRule.onNodeWithTag("echo-now-bar-surface")
            .assertHeightIsEqualTo(EchoMobileLayoutMetrics.nowBarHeight)
    }

    @Test
    fun horizontalTrackSwipeKeepsPauseWorkingBeforeAndAfterExpand() {
        var playClicks = 0

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowBar(
                    state = progressState().let { state ->
                        state.copy(
                            progress = state.progress.copy(playing = true),
                            lyrics = state.lyrics.copy(
                                lines = listOf(LyricUiLine("Capsule lyric", active = true))
                            ),
                            labels = state.labels.copy(play = "Play", pause = "Pause")
                        )
                    },
                    waveformExpanded = false,
                    onExpandWaveform = { },
                    onCollapseWaveform = { },
                    onPrevious = Runnable { },
                    onPlayPause = Runnable { playClicks += 1 },
                    onNext = Runnable { },
                    onFavorite = Runnable { },
                    onShuffle = Runnable { },
                    onRepeat = Runnable { },
                    onOpenNowPlaying = Runnable { },
                    onOpenQueue = Runnable { },
                    onSeek = SeekAction { }
                )
            }
        }

        composeRule.onNodeWithText("Track").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.15f, y))
            moveTo(Offset(visibleSize.width * 0.90f, y), 280L)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Expand Now Bar").assertExists()
        composeRule.onNodeWithText("Capsule lyric").assertExists()
        composeRule.onNodeWithContentDescription("Pause").performTouchInput {
            down(Offset(visibleSize.width / 2f, visibleSize.height / 2f))
            up()
        }
        composeRule.runOnIdle { assertTrue(playClicks == 1) }

        composeRule.onNodeWithContentDescription("Expand Now Bar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Track").assertExists()
        composeRule.onNodeWithContentDescription("Pause").performTouchInput {
            down(Offset(visibleSize.width / 2f, visibleSize.height / 2f))
            up()
        }
        composeRule.runOnIdle { assertTrue(playClicks == 2) }
    }

    private fun progressState(): NowBarState = nowBarEmptyState().let { state ->
        state.copy(
            track = state.track.copy(
                title = "Track",
                subtitle = "Artist",
                canExpand = true
            ),
            progress = state.progress.copy(
                elapsed = "0:25",
                duration = "1:40",
                positionMs = 25_000L,
                durationMs = 100_000L
            ),
            labels = state.labels.copy(
                playbackProgress = "Playback progress",
                expandWaveform = "Expand waveform",
                dockLeft = "Dock Now Bar left",
                dockRight = "Dock Now Bar right",
                expandNowBar = "Expand Now Bar"
            )
        )
    }
}
