package app.yukine.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

        composeRule.onNodeWithContentDescription("Playback progress").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.2f, y))
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
    fun scrollCompactedNowBarKeepsProgressAndPlaybackControlsInteractive() {
        val seekPositions = mutableListOf<Long>()
        var playClicks = 0

        composeRule.setContent {
            EchoTheme.EchoTheme {
                CompositionLocalProvider(LocalEchoNowBarCompactProgress provides 1f) {
                    NowBar(
                        state = progressState().copy(playLabel = "Play"),
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
                        onSeek = SeekAction { position -> seekPositions += position }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Playback progress").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.3f, y))
            moveTo(Offset(visibleSize.width * 0.7f, y))
            up()
        }
        composeRule.onNodeWithContentDescription("Play").performClick()

        composeRule.runOnIdle {
            assertEquals(1, seekPositions.size)
            assertTrue(playClicks == 1)
        }
    }

    @Test
    fun horizontalTrackSwipeKeepsPauseWorkingBeforeAndAfterExpand() {
        var playClicks = 0

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowBar(
                    state = progressState().copy(
                        playing = true,
                        playLabel = "Play",
                        pauseLabel = "Pause",
                        lyrics = listOf(LyricUiLine("Capsule lyric", active = true))
                    ),
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

    private fun progressState(): NowBarState = nowBarEmptyState().copy(
        title = "Track",
        subtitle = "Artist",
        elapsed = "0:25",
        duration = "1:40",
        positionMs = 25_000L,
        durationMs = 100_000L,
        canExpand = true,
        playbackProgressLabel = "Playback progress",
        expandWaveformLabel = "Expand waveform",
        dockLeftLabel = "Dock Now Bar left",
        dockRightLabel = "Dock Now Bar right",
        expandNowBarLabel = "Expand Now Bar"
    )
}
