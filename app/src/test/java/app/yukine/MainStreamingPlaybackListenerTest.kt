package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainStreamingPlaybackListenerTest {
    @Test
    fun delegatesStreamingPlaybackCallbacksToInjectedOwners() {
        val queue = listOf(streamingPlaybackTrack(1L), streamingPlaybackTrack(2L))
        val snapshot = PlaybackStateSnapshot.empty()
        val result = PlaybackActionResultUi(snapshot, "played", true, true, true, false)
        val appendedSnapshots = mutableListOf<PlaybackStateSnapshot>()
        val appliedResults = mutableListOf<PlaybackActionResultUi?>()
        val statuses = mutableListOf<String>()
        val listener = MainStreamingPlaybackListener(
            languageProvider = StreamingPlaybackLanguageProvider { "zh-CN" },
            adaptiveQualityProvider = AdaptiveStreamingQualityProvider { StreamingAudioQuality.HIGH },
            selectedQualityProvider = SelectedStreamingQualityProvider { StreamingAudioQuality.LOSSLESS },
            queueSnapshotSource = StreamingQueueSnapshotSource { queue },
            heartbeatAppendHandler = HeartbeatRecommendationAppendHandler { appendedSnapshots += it },
            resultSink = PlaybackActionResultSink { appliedResults += it },
            statusSink = StreamingPlaybackStatusSink { statuses += it }
        )

        assertEquals("zh-CN", listener.languageMode())
        assertEquals(StreamingAudioQuality.HIGH, listener.adaptiveStreamingQuality())
        assertEquals(StreamingAudioQuality.LOSSLESS, listener.selectedStreamingQuality())
        assertEquals(queue, listener.queueSnapshot())
        listener.maybeAppendHeartbeatRecommendations(snapshot)
        listener.applyPlaybackActionResult(result)
        listener.setStatus("Resolving")

        assertEquals(listOf(snapshot), appendedSnapshots)
        assertSame(result, appliedResults.single())
        assertEquals(listOf("Resolving"), statuses)
    }

    @Test
    fun factoryCreatesStreamingPlaybackControllerListener() {
        val factory = PlaybackUiModule.provideMainStreamingPlaybackListenerFactory()
        val snapshot = PlaybackStateSnapshot.empty()
        val calls = mutableListOf<String>()
        val listener = factory.create(
            StreamingPlaybackLanguageProvider { "en" },
            AdaptiveStreamingQualityProvider { StreamingAudioQuality.STANDARD },
            SelectedStreamingQualityProvider { StreamingAudioQuality.HIRES },
            StreamingQueueSnapshotSource { listOf(streamingPlaybackTrack(3L)) },
            HeartbeatRecommendationAppendHandler { calls += "heartbeat:${it.queueSize}" },
            PlaybackActionResultSink { calls += "result:${it?.status}" },
            StreamingPlaybackStatusSink { calls += "status:$it" }
        )
        val result = PlaybackActionResultUi(null, "queued", false, false, false, false)

        assertEquals("en", listener.languageMode())
        assertEquals(StreamingAudioQuality.STANDARD, listener.adaptiveStreamingQuality())
        assertEquals(StreamingAudioQuality.HIRES, listener.selectedStreamingQuality())
        assertEquals(listOf(3L), listener.queueSnapshot().map { it.id })
        listener.maybeAppendHeartbeatRecommendations(snapshot)
        listener.applyPlaybackActionResult(result)
        listener.setStatus("Queued")

        assertEquals(listOf("heartbeat:0", "result:queued", "status:Queued"), calls)
    }
}

private fun streamingPlaybackTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
