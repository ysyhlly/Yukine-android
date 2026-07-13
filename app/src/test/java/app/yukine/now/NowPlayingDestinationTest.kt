package app.yukine.now

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.yukine.NowPlayingUiState
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.EchoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NowPlayingDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun librarySourceCandidates_renderAndSwitchFromTheNowPlayingSourceList() {
        val current = track(
            id = 101L,
            dataPath = "file:///music/current.mp3",
            codec = "mp3"
        )
        val alternate = track(
            id = 102L,
            dataPath = "file:///music/alternate.flac",
            codec = "flac"
        )
        var switchedTo: Track? = null
        val state = MutableStateFlow(
            NowPlayingUiState(
                trackTitle = current.title,
                artist = current.artist,
                album = current.album,
                trackId = current.id,
                currentTrack = current,
                durationMs = current.durationMs
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(
                    state = state,
                    sourceCandidates = { listOf(current, alternate) },
                    onSwitchLocalSource = { _, replacement -> switchedTo = replacement }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("alternate.flac"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alternate.flac").assertIsDisplayed().performClick()

        assertEquals(alternate.id, switchedTo?.id)
    }

    @Test
    fun sameProviderQualityVariantsRenderOnceAndOtherProviderSwitchesHot() {
        val current = streamingTrack(
            """[
                {"provider":"qqmusic","providerTrackId":"qq-song","label":"qmusic","available":true},
                {"provider":"qqmusic","providerTrackId":"qq-song","quality":"standard","label":"QQ 音乐","available":true},
                {"provider":"qqmusic","providerTrackId":"qq-song","quality":"high","label":"QQ 音乐","available":true},
                {"provider":"netease","providerTrackId":"netease-song","quality":"lossless","label":"网易云音乐","available":true}
            ]"""
        )
        var switchRequest: StreamingSwitchRequest? = null
        val state = MutableStateFlow(
            NowPlayingUiState(
                trackTitle = current.title,
                artist = current.artist,
                album = current.album,
                trackId = current.id,
                currentTrack = current,
                durationMs = current.durationMs
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(
                    state = state,
                    onSwitchSource = { _, provider, providerTrackId, quality ->
                        switchRequest = StreamingSwitchRequest(provider, providerTrackId, quality)
                    }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("QQ 音乐 / HIGH"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("QQ 音乐 / HIGH").assertIsDisplayed()
        composeRule.onAllNodesWithText("QQ 音乐 / STANDARD").assertCountEquals(0)
        composeRule.onAllNodesWithText("qmusic").assertCountEquals(0)
        composeRule.onNodeWithText("网易云音乐").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(
            StreamingSwitchRequest(
                StreamingProviderName.NETEASE,
                "netease-song",
                StreamingAudioQuality.LOSSLESS
            ),
            switchRequest
        )
    }

    @Test
    fun unavailableHigherQualityFallsBackToTheHighestAvailableVariant() {
        val current = streamingTrack(
            """[
                {"provider":"qqmusic","providerTrackId":"qq-song","quality":"standard","label":"QQ 音乐","available":true},
                {"provider":"qqmusic","providerTrackId":"qq-song","quality":"high","label":"QQ 音乐","available":false},
                {"provider":"netease","providerTrackId":"netease-song","label":"网易云音乐","available":true}
            ]"""
        )
        val state = MutableStateFlow(
            NowPlayingUiState(
                trackTitle = current.title,
                artist = current.artist,
                album = current.album,
                trackId = current.id,
                currentTrack = current,
                durationMs = current.durationMs
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(state = state)
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("QQ 音乐 / STANDARD"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("QQ 音乐 / STANDARD").assertIsDisplayed()
        composeRule.onAllNodesWithText("QQ 音乐 / HIGH").assertCountEquals(0)
    }

    private fun track(id: Long, dataPath: String, codec: String): Track = Track(
        id,
        "Same Song",
        "Artist",
        "Album",
        180_000L,
        Uri.EMPTY,
        dataPath,
        0L,
        null,
        codec,
        1_000,
        44_100,
        16,
        2
    )

    private fun streamingTrack(sourceOptions: String): Track = Track(
        201L,
        "Streaming Song",
        "Artist",
        "Album",
        180_000L,
        Uri.EMPTY,
        "streaming:qqmusic:qq-song?sourceOptions=" +
            URLEncoder.encode(sourceOptions, StandardCharsets.UTF_8.name()),
        0L,
        null,
        "aac",
        320,
        44_100,
        16,
        2
    )

    private data class StreamingSwitchRequest(
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality?
    )
}
