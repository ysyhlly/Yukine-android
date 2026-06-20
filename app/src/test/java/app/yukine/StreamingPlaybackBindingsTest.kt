package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaybackBindingsTest {
    @Test
    fun forwardsStreamingPlaybackEdges() {
        val calls = mutableListOf<String>()
        val bindings = StreamingPlaybackBindings(
            languageModeProvider = StatusLanguageModeProvider {
                calls += "language"
                AppLanguage.MODE_ENGLISH
            },
            adaptiveQualityProvider = StreamingPlaybackControllerQualityProvider {
                calls += "adaptive"
                StreamingAudioQuality.HIGH
            },
            selectedQualityProvider = StreamingPlaybackControllerQualityProvider {
                calls += "selected"
                StreamingAudioQuality.LOSSLESS
            },
            queueProvider = StreamingPlaybackQueueProvider {
                calls += "queue"
                listOf(streamingPlaybackTrack(1L))
            },
            heartbeatAppender = StreamingHeartbeatAppender {
                calls += "heartbeat:${it.currentTrack?.id}"
            },
            playbackActionResultApplier = QueuePlaybackActionResultApplier { result ->
                calls += "result:${result?.status}"
            },
            statusSink = QueueStatusSink { status ->
                calls += "status:$status"
            }
        )

        bindings.languageMode()
        bindings.adaptiveStreamingQuality()
        bindings.selectedStreamingQuality()
        bindings.queueSnapshot()
        bindings.maybeAppendHeartbeatRecommendations(snapshot())
        bindings.applyPlaybackActionResult(PlaybackActionResultUi(null, "played", false, false, false, false))
        bindings.setStatus("Ready")

        assertEquals(
            listOf("language", "adaptive", "selected", "queue", "heartbeat:7", "result:played", "status:Ready"),
            calls
        )
    }

    private fun snapshot(): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            streamingPlaybackTrack(7L),
            0,
            1,
            0L,
            1000L,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
}

private fun streamingPlaybackTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
