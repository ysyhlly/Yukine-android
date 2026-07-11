package app.yukine.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertFalse
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
            up()
        }

        composeRule.runOnIdle {
            assertTrue(seekPositions.isNotEmpty())
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

    private fun progressState(): NowBarState = nowBarEmptyState().copy(
        title = "Track",
        subtitle = "Artist",
        elapsed = "0:25",
        duration = "1:40",
        positionMs = 25_000L,
        durationMs = 100_000L,
        playbackProgressLabel = "Playback progress",
        expandWaveformLabel = "Expand waveform"
    )
}
