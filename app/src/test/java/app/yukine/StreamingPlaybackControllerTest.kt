package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingPlaybackControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(): StreamingViewModel {
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(Dispatchers.Main)
        return viewModel
    }

    @Test
    fun preResolveNextStreamingTrackReusesOneQueueSnapshotForNextAndWindow() {
        val queue = listOf(
            localTrack(1L),
            streamingPlaceholderTrack(2L, "next-2"),
            streamingPlaceholderTrack(3L, "next-3")
        )
        val listener = FakeListener(queue)
        val controller = StreamingPlaybackController(
            createViewModel(),
            NowPlayingViewModel(),
            listener
        )

        controller.preResolveNextStreamingTrack(
            playbackSnapshot(
                currentTrack = queue[0],
                currentIndex = 0,
                queueSize = queue.size,
                playing = true
            )
        )

        assertEquals(1, listener.queueSnapshotCalls)
        assertEquals(1, listener.heartbeatSnapshots.size)
    }

    @Test
    fun preResolveNextStreamingTrackDoesNotReadQueueWhenPlaybackIsNotActive() {
        val listener = FakeListener(listOf(localTrack(1L)))
        val controller = StreamingPlaybackController(
            createViewModel(),
            NowPlayingViewModel(),
            listener
        )

        controller.preResolveNextStreamingTrack(
            playbackSnapshot(
                currentTrack = null,
                currentIndex = -1,
                queueSize = 0,
                playing = false
            )
        )

        assertEquals(0, listener.queueSnapshotCalls)
        assertEquals(0, listener.heartbeatSnapshots.size)
    }
}

private class FakeListener(
    private val queue: List<Track>
) : StreamingPlaybackController.Listener {
    var queueSnapshotCalls = 0
    val heartbeatSnapshots = mutableListOf<PlaybackStateSnapshot>()
    val results = mutableListOf<PlaybackActionResultUi?>()
    val statuses = mutableListOf<String>()

    override fun languageMode(): String = AppLanguage.MODE_ENGLISH

    override fun adaptiveStreamingQuality(): StreamingAudioQuality = StreamingAudioQuality.HIGH

    override fun selectedStreamingQuality(): StreamingAudioQuality = StreamingAudioQuality.LOSSLESS

    override fun queueSnapshot(): List<Track> {
        queueSnapshotCalls += 1
        return queue
    }

    override fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot) {
        heartbeatSnapshots += snapshot
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        results += result
    }

    override fun setStatus(status: String) {
        statuses += status
    }
}

private fun playbackSnapshot(
    currentTrack: Track?,
    currentIndex: Int,
    queueSize: Int,
    playing: Boolean
): PlaybackStateSnapshot =
    PlaybackStateSnapshot(
        currentTrack,
        currentIndex,
        queueSize,
        1_000L,
        120_000L,
        playing,
        false,
        "",
        false,
        0,
        1f,
        1f,
        0L
    )

private fun localTrack(id: Long): Track =
    Track(id, "Local $id", "Artist", "Album", 1_000L, Uri.EMPTY, "local:$id")

private fun streamingPlaceholderTrack(id: Long, providerTrackId: String): Track =
    StreamingPlaybackAdapter.placeholderTrack(
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = providerTrackId,
            title = "Streaming $id",
            artist = "Artist"
        )
    ).let { placeholder ->
        Track(
            id,
            placeholder.title,
            placeholder.artist,
            placeholder.album,
            placeholder.durationMs,
            placeholder.contentUri,
            placeholder.dataPath
        )
    }
