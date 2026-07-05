package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainNowPlayingGatewayTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun transportActionsUsePlaybackActionControllerAndSeekHandler() {
        val playbackGateway = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(playbackGateway)
        val listener = FakePlaybackActionListener()
        val controller = PlaybackActionController(viewModel, listener)
        val seeks = mutableListOf<Long>()
        val gateway = MainNowPlayingGateway(
            playbackActionControllerProvider = PlaybackActionControllerProvider { controller },
            playbackStoreProvider = MainPlaybackStoreProvider { null },
            favoriteToggler = NowPlayingFavoriteToggler {},
            seekHandler = NowPlayingSeekHandler { seeks += it },
            statusTextProvider = NowPlayingStatusTextProvider { "text:$it" }
        )

        gateway.playPause()
        gateway.next()
        gateway.previous()
        gateway.toggleShuffle()
        gateway.cycleRepeatMode()
        gateway.seekTo(42_000L)

        assertEquals(listOf("play", "next", "previous", "shuffle:true", "repeat"), playbackGateway.calls)
        assertEquals(listOf("resolve", "resolve", "resolve"), listener.resolveCalls)
        assertEquals(listOf(42_000L), seeks)
        assertEquals("text:no.track", gateway.statusMessage("no.track"))
    }

    @Test
    fun toggleFavoriteUsesCurrentPlaybackStoreTrack() {
        val playbackViewModel = PlaybackViewModel()
        val store = MainPlaybackStore(playbackViewModel)
        val track = track(7L)
        playbackViewModel.replacePlaybackSnapshot(snapshot(track))
        val favorites = mutableListOf<Track>()
        val gateway = MainNowPlayingGateway(
            playbackActionControllerProvider = PlaybackActionControllerProvider { null },
            playbackStoreProvider = MainPlaybackStoreProvider { store },
            favoriteToggler = NowPlayingFavoriteToggler { favorites += it },
            seekHandler = NowPlayingSeekHandler {},
            statusTextProvider = NowPlayingStatusTextProvider { it }
        )

        gateway.toggleFavorite()

        assertEquals(listOf(7L), favorites.map { it.id })
    }

    @Test
    fun toggleFavoriteIgnoresMissingTrack() {
        val playbackViewModel = PlaybackViewModel()
        val store = MainPlaybackStore(playbackViewModel)
        val favorites = mutableListOf<Track>()
        val gateway = MainNowPlayingGateway(
            playbackActionControllerProvider = PlaybackActionControllerProvider { null },
            playbackStoreProvider = MainPlaybackStoreProvider { store },
            favoriteToggler = NowPlayingFavoriteToggler { favorites += it },
            seekHandler = NowPlayingSeekHandler {},
            statusTextProvider = NowPlayingStatusTextProvider { it }
        )

        gateway.toggleFavorite()

        assertEquals(emptyList<Track>(), favorites)
    }

    private class FakePlaybackActionListener : PlaybackActionController.Listener {
        val resolveCalls = mutableListOf<String>()

        override fun resolveCurrentStreamingQueueTrackIfNeeded(): Boolean {
            resolveCalls += "resolve"
            return false
        }

        override fun playbackSnapshot(): PlaybackStateSnapshot = snapshot(track(1L))

        override fun fallbackTracks(): List<Track> = listOf(track(1L), track(2L))

        override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {}
    }

    private class FakePlaybackGateway : NowPlayingPlaybackGateway {
        val calls = mutableListOf<String>()

        override fun snapshot(): PlaybackStateSnapshot? = snapshot(track(1L))
        override fun skipToPrevious() {
            calls += "previous"
        }
        override fun skipToNext() {
            calls += "next"
        }
        override fun seekTo(positionMs: Long) {}
        override fun removeTracksById(trackIds: Set<Long>) {}
        override fun clearQueue() {}
        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {}
        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {}
        override fun retainTracksById(trackIds: Set<Long>) {}
        override fun warmPlaybackTrack(track: Track) {}
        override fun appendToQueue(tracks: List<Track>) {}
        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {}
        override fun startSleepTimerMinutes(minutes: Int) {}
        override fun cancelSleepTimer() {}
        override fun playQueue(tracks: List<Track>, index: Int) {}
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

private fun snapshot(track: Track): PlaybackStateSnapshot =
    PlaybackStateSnapshot(
        track,
        0,
        1,
        0L,
        1_000L,
        false,
        false,
        "",
        false,
        EchoPlaybackService.REPEAT_ALL,
        1.0f,
        1.0f,
        0L
    )
