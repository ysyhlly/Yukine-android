package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPlaybackGatewayAdapterTest {
    @Test
    fun forwardsNowPlayingCommandsThroughServicePort() {
        val service = FakeNowPlayingPlaybackServicePort()
        val startedActions = mutableListOf<String?>()
        val gateway = NowPlayingPlaybackGatewayAdapter(
            serviceProvider = { service },
            serviceStarter = { startedActions += it }
        )
        val track = track(7L)

        gateway.playQueue(listOf(track), 0)
        gateway.warmPlaybackTrack(track)
        gateway.skipToNext()
        gateway.pause()

        assertTrue(gateway.serviceConnected())
        assertTrue(gateway.hasQueue())
        assertEquals(listOf<String?>(null), startedActions)
        assertEquals(
            listOf("playQueue:1:0", "warm:7", "next", "pause"),
            service.calls
        )
    }

    @Test
    fun missingServiceKeepsCommandsAsNoOps() {
        val startedActions = mutableListOf<String?>()
        val gateway = NowPlayingPlaybackGatewayAdapter(
            serviceProvider = { null },
            serviceStarter = { startedActions += it }
        )

        gateway.play()
        gateway.skipToPrevious()
        gateway.skipToNext()

        assertEquals(
            listOf(
                null,
                "app.yukine.action.PREVIOUS",
                "app.yukine.action.NEXT"
            ),
            startedActions
        )
        assertEquals(false, gateway.serviceConnected())
    }

    private class FakeNowPlayingPlaybackServicePort : NowPlayingPlaybackServicePort {
        val calls = mutableListOf<String>()

        override fun snapshot(): PlaybackStateSnapshot? = null
        override fun hasQueue(): Boolean = true
        override fun skipToPrevious() {
            calls += "previous"
        }

        override fun skipToNext() {
            calls += "next"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seek:$positionMs"
        }

        override fun removeTracksById(trackIds: Set<Long>) {
            calls += "remove:${trackIds.size}"
        }

        override fun clearQueue() {
            calls += "clear"
        }

        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
            calls += "move:$fromIndex:$toIndex"
        }

        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
            calls += "replaceById:$oldTrackId:${updated.id}"
        }

        override fun retainTracksById(trackIds: Set<Long>) {
            calls += "retain:${trackIds.size}"
        }

        override fun warmPlaybackTrack(track: Track) {
            calls += "warm:${track.id}"
        }

        override fun appendToQueue(tracks: List<Track>) {
            calls += "append:${tracks.size}"
        }

        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
            calls += "replaceCurrent:${track.id}:$positionMs"
        }

        override fun startSleepTimerMinutes(minutes: Int) {
            calls += "sleep:$minutes"
        }

        override fun cancelSleepTimer() {
            calls += "cancelSleep"
        }

        override fun playQueue(tracks: List<Track>, index: Int) {
            calls += "playQueue:${tracks.size}:$index"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun play() {
            calls += "play"
        }

        override fun setShuffleEnabled(enabled: Boolean) {
            calls += "shuffle:$enabled"
        }

        override fun cycleRepeatMode() {
            calls += "repeat"
        }

        override fun setRepeatMode(repeatMode: Int) {
            calls += "repeat:$repeatMode"
        }
    }
}

private fun track(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
