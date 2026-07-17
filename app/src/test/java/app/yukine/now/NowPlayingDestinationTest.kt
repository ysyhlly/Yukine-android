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
import app.yukine.NowPlayingProgressState
import app.yukine.NowPlayingTrackState
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.PlaybackSourcePolicySnapshot
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderDescriptor
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
                track = NowPlayingTrackState(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    trackId = current.id,
                    currentTrack = current
                ),
                progress = NowPlayingProgressState(durationMs = current.durationMs)
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
                track = NowPlayingTrackState(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    trackId = current.id,
                    currentTrack = current
                ),
                progress = NowPlayingProgressState(durationMs = current.durationMs)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(
                    state = state,
                    playbackSourcePolicy = PlaybackSourcePolicySnapshot(
                        enabledRemoteProviders = setOf(
                            StreamingProviderName.LUOXUE,
                            StreamingProviderName.NETEASE
                        )
                    ),
                    onSwitchSource = { _, provider, providerTrackId, quality ->
                        switchRequest = StreamingSwitchRequest(provider, providerTrackId, quality)
                    }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("仅用于曲库与同步，不提供播放音源 / HIGH")
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("仅用于曲库与同步，不提供播放音源 / HIGH").assertIsDisplayed()
        composeRule.onAllNodesWithText("仅用于曲库与同步，不提供播放音源 / STANDARD").assertCountEquals(0)
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
    fun onlyClosestOrderedLxSourceRendersAndSwitchesHot() {
        val current = streamingTrack(
            """[
                {"provider":"luoxue","providerTrackId":"wy:main","label":"洛雪音源 · WY · Streaming Song","available":true},
                {"provider":"luoxue","providerTrackId":"tx:live","label":"洛雪音源 · TX · Streaming Song (Live)","available":true}
            ]"""
        )
        var switchRequest: StreamingSwitchRequest? = null
        val state = MutableStateFlow(
            NowPlayingUiState(
                track = NowPlayingTrackState(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    trackId = current.id,
                    currentTrack = current
                ),
                progress = NowPlayingProgressState(durationMs = current.durationMs)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(
                    state = state,
                    playbackSourcePolicy = PlaybackSourcePolicySnapshot(
                        enabledRemoteProviders = setOf(StreamingProviderName.LUOXUE)
                    ),
                    onSwitchSource = { _, provider, providerTrackId, quality ->
                        switchRequest = StreamingSwitchRequest(provider, providerTrackId, quality)
                    }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("洛雪音源 · WY · Streaming Song")
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("洛雪音源 · TX · Streaming Song (Live)")
            .assertCountEquals(0)
        composeRule.onNodeWithText("洛雪音源 · WY · Streaming Song")
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            StreamingSwitchRequest(StreamingProviderName.LUOXUE, "wy:main", null),
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
                track = NowPlayingTrackState(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    trackId = current.id,
                    currentTrack = current
                ),
                progress = NowPlayingProgressState(durationMs = current.durationMs)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(state = state)
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("仅用于曲库与同步，不提供播放音源 / STANDARD")
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("仅用于曲库与同步，不提供播放音源 / STANDARD").assertIsDisplayed()
        composeRule.onAllNodesWithText("仅用于曲库与同步，不提供播放音源 / HIGH").assertCountEquals(0)
    }

    @Test
    fun everyAddedProviderIsShownWithCurrentOrNoSourceStatus() {
        val current = streamingTrack("[]")
        val state = MutableStateFlow(
            NowPlayingUiState(
                track = NowPlayingTrackState(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    trackId = current.id,
                    currentTrack = current
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                NowPlayingDestination(
                    state = state,
                    streamingProviders = listOf(
                        provider(StreamingProviderName.QQ_MUSIC, "QQ 音乐"),
                        provider(StreamingProviderName.NETEASE, "网易云音乐")
                    )
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("设置中未开启"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("仅用于曲库与同步，不提供播放音源").assertIsDisplayed()
        composeRule.onNodeWithText("设置中未开启").assertIsDisplayed()
        composeRule.onNodeWithText("网易云音乐").performScrollTo().assertIsDisplayed()
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

    private fun provider(name: StreamingProviderName, displayName: String) = StreamingProviderDescriptor(
        name = name,
        displayName = displayName,
        capabilities = StreamingProviderCapabilities(
            supportsSearch = true,
            supportsPlayback = true
        )
    )

    private data class StreamingSwitchRequest(
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality?
    )
}
