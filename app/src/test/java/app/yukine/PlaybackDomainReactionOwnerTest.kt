package app.yukine

import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackDomainReactionOwnerTest {
    @Test
    fun busyMainHandlerKeepsOnlyTheLatestPlaybackSnapshot() {
        val actions = RecordingActions()
        val owner = owner(actions)
        val current = track(7L)

        owner.onPlaybackStateChanged(snapshot(track = current, positionMs = 1_000L, queueSize = 1))
        owner.onPlaybackStateChanged(snapshot(track = current, positionMs = 2_000L, queueSize = 1))
        owner.onPlaybackStateChanged(snapshot(track = current, positionMs = 3_000L, queueSize = 1))
        idleMain()

        assertEquals(listOf(3_000L), actions.preResolvedPositions)
    }

    @Test
    fun coalescedTrackTransitionUsesLatestTrackForDomainReactions() {
        val actions = RecordingActions()
        val owner = owner(actions)

        owner.onPlaybackStateChanged(snapshot(track = track(1L), positionMs = 1_000L, queueSize = 2))
        owner.onPlaybackStateChanged(snapshot(track = track(2L), positionMs = 0L, queueSize = 2))
        idleMain()

        assertEquals(listOf(2L), actions.loadedLyricsTrackIds)
        assertEquals(listOf(2L), actions.preResolvedTrackIds)
    }

    @Test
    fun busyMainHandlerKeepsOnlyTheLatestBufferingSnapshot() {
        val actions = RecordingActions()
        val owner = owner(actions)
        val current = track(8L)

        owner.onPlaybackBuffering(snapshot(track = current, positionMs = 1_000L, queueSize = 1))
        owner.onPlaybackBuffering(snapshot(track = current, positionMs = 2_000L, queueSize = 1))
        owner.onPlaybackBuffering(snapshot(track = current, positionMs = 3_000L, queueSize = 1))
        idleMain()

        assertEquals(listOf("buffering:3000"), actions.calls)
    }

    @Test
    fun resolvedStreamingPlaybackErrorRefreshesUrlAndSuppressesStaleError() {
        val actions = RecordingActions(resolveStreamingResult = true)
        val owner = owner(actions)
        val streaming = Track(
            77L,
            "Streaming",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://expired.example.test/song.mp3"),
            "streaming:netease:song-77"
        )

        owner.onPlaybackStateChanged(
            snapshot(streaming, 10_000L, 1, errorMessage = "Unable to play this track.")
        )
        idleMain()

        assertEquals(1, actions.streamingResolveCalls)
        assertEquals(emptyList<String>(), actions.statuses)
    }

    @Test
    fun bufferingAndSettingsCallbacksReachTheirDomainOwners() {
        val actions = RecordingActions()
        val owner = owner(actions)
        val snapshot = snapshot(track(9L), 4_000L, 1)

        owner.onPlaybackBuffering(snapshot)
        owner.onPlaybackStateChanged(snapshot)
        idleMain()

        assertEquals(
            listOf("buffering:4000", "collections", "settings:1.0:1.0"),
            actions.calls.sorted()
        )
    }

    private fun owner(actions: RecordingActions): PlaybackDomainReactionOwner =
        PlaybackDomainReactionOwner(
            mainHandler = Handler(Looper.getMainLooper()),
            currentLyricsTrackIdSource = PlaybackDomainReactionOwner.CurrentLyricsTrackIdSource { -1L },
            playbackSettingsSaver = PlaybackDomainReactionOwner.PlaybackSettingsSaver { speed, volume ->
                actions.calls += "settings:$speed:$volume"
            },
            lyricsLoader = PlaybackDomainReactionOwner.LyricsLoader { track ->
                track?.let { actions.loadedLyricsTrackIds += it.id }
            },
            collectionsLoader = PlaybackDomainReactionOwner.CollectionsLoader { actions.calls += "collections" },
            nextStreamingTrackPreResolver = PlaybackDomainReactionOwner.NextStreamingTrackPreResolver { snapshot ->
                actions.preResolvedPositions += snapshot.positionMs
                snapshot.currentTrack?.let { actions.preResolvedTrackIds += it.id }
            },
            streamingBufferingRecoveryHandler = PlaybackDomainReactionOwner.StreamingBufferingRecoveryHandler {
                actions.calls += "buffering:${it.positionMs}"
            },
            currentStreamingTrackResolver = PlaybackDomainReactionOwner.CurrentStreamingTrackResolver {
                actions.streamingResolveCalls += 1
                actions.resolveStreamingResult
            },
            statusSink = PlaybackDomainReactionOwner.StatusSink { actions.statuses += it }
        )

    private class RecordingActions(
        val resolveStreamingResult: Boolean = false
    ) {
        val calls = mutableListOf<String>()
        val preResolvedPositions = mutableListOf<Long>()
        val loadedLyricsTrackIds = mutableListOf<Long>()
        val preResolvedTrackIds = mutableListOf<Long>()
        var streamingResolveCalls = 0
        val statuses = mutableListOf<String>()
    }

    private fun snapshot(
        track: Track,
        positionMs: Long,
        queueSize: Int,
        queueRevision: Long = 0L,
        errorMessage: String = ""
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            queueSize,
            positionMs,
            track.durationMs,
            true,
            false,
            errorMessage,
            false,
            0,
            1.0f,
            1.0f,
            0L,
            queueRevision
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file:$id")

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
