package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingViewModelTest {
    @Test
    fun transportEventsCallGateway() {
        val gateway = FakeGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindGateway(gateway)

        viewModel.onEvent(NowPlayingEvent.PlayPause)
        viewModel.onEvent(NowPlayingEvent.Next)
        viewModel.onEvent(NowPlayingEvent.Previous)
        viewModel.onEvent(NowPlayingEvent.SeekTo(42_000L))

        assertEquals(listOf("playPause", "next", "previous", "seek:42000"), gateway.calls)
    }

    @Test
    fun toggleLyricsUpdatesStateOnly() {
        val gateway = FakeGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindGateway(gateway)
        viewModel.updateState(snapshotWithTrack(), setOf(7L), null)

        assertFalse(viewModel.uiState.value.lyricsVisible)
        viewModel.onEvent(NowPlayingEvent.ToggleLyrics)

        assertTrue(viewModel.uiState.value.lyricsVisible)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun openQueueEmitsEffect() {
        val viewModel = NowPlayingViewModel()

        viewModel.onEvent(NowPlayingEvent.OpenQueue)

        assertEquals(listOf(NowPlayingEffect.OpenQueue), viewModel.drainEffects())
    }

    @Test
    fun addToPlaylistEmitsCurrentTrackEffect() {
        val viewModel = NowPlayingViewModel()
        viewModel.updateState(snapshotWithTrack(), emptySet(), null)

        viewModel.onEvent(NowPlayingEvent.AddToPlaylist)

        val effect = viewModel.drainEffects().single() as NowPlayingEffect.OpenAddToPlaylist
        assertEquals(7L, effect.track.id)
        assertEquals("Song", effect.track.title)
    }

    @Test
    fun downloadCurrentTrackEmitsCurrentTrackEffect() {
        val viewModel = NowPlayingViewModel()
        viewModel.updateState(snapshotWithTrack(), emptySet(), null)

        viewModel.onEvent(NowPlayingEvent.DownloadCurrentTrack)

        val effect = viewModel.drainEffects().single() as NowPlayingEffect.DownloadTrack
        assertEquals(7L, effect.track.id)
        assertEquals("Song", effect.track.title)
    }

    @Test
    fun shareCurrentTrackEmitsCurrentTrackEffect() {
        val viewModel = NowPlayingViewModel()
        viewModel.updateState(snapshotWithTrack(), emptySet(), null)

        viewModel.onEvent(NowPlayingEvent.ShareCurrentTrack)

        val effect = viewModel.drainEffects().single() as NowPlayingEffect.ShareTrack
        assertEquals(7L, effect.track.id)
        assertEquals("Song", effect.track.title)
    }

    @Test
    fun addToPlaylistWithoutTrackDoesNotCrash() {
        val gateway = FakeGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindGateway(gateway)
        viewModel.updateState(PlaybackStateSnapshot.empty(), emptySet(), null)

        viewModel.onEvent(NowPlayingEvent.AddToPlaylist)

        assertEquals(listOf(NowPlayingEffect.ShowMessage("No track")), viewModel.drainEffects())
    }

    @Test
    fun stateMirrorsPlaybackSnapshot() {
        val viewModel = NowPlayingViewModel()
        viewModel.updateState(snapshotWithTrack(playing = true, positionMs = 5_000L), setOf(7L), null)

        val state = viewModel.uiState.value
        assertEquals("Song", state.trackTitle)
        assertEquals("Artist", state.artist)
        assertEquals("Album", state.album)
        assertTrue(state.isPlaying)
        assertEquals(5_000L, state.positionMs)
        assertEquals(180_000L, state.durationMs)
        assertTrue(state.isFavorite)
        assertEquals(RepeatModeUi.All, state.repeatMode)
    }

    @Test
    fun playbackActionsDelegateToPlaybackGatewayAndReturnResults() {
        val player = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        val track = Track(9L, "Queued", "Artist", "Album", 120_000L, Uri.EMPTY, "file:queued.mp3")
        player.queue = listOf(track)
        viewModel.bindPlaybackGateway(player)

        assertTrue(viewModel.hasQueue())
        val removeResult = viewModel.removeQueueTrack(track)
        val clearResult = viewModel.clearQueue()
        val sleepResult = viewModel.startSleepTimer(20)
        val cancelResult = viewModel.cancelSleepTimer()
        val playResult = viewModel.playTrackList(listOf(track), 0)
        viewModel.seekTo(4_000L)
        viewModel.skipToPrevious()
        viewModel.skipToNext()
        viewModel.replaceQueuedTrack(8L, track)
        viewModel.retainTracks(listOf(track))
        viewModel.precacheTrack(track)
        viewModel.appendToQueue(listOf(track))
        viewModel.replaceCurrentTrackAndResume(track, 7_000L)

        assertEquals(
            listOf(
                "remove:9",
                "clear",
                "sleep:20",
                "cancelSleep",
                "playQueue:1:0",
                "seek:4000",
                "previous",
                "next",
                "replaceById:8:9",
                "retain:9",
                "precache:9",
                "append:1",
                "replaceCurrent:9:7000"
            ),
            player.calls
        )
        assertEquals("Status: Queued", removeResult.status)
        assertTrue(removeResult.publishPlaybackState)
        assertEquals("Status", clearResult.status)
        assertEquals("Sleep timer set: 20 minutes", sleepResult.status)
        assertEquals("Sleep timer cancelled", cancelResult.status)
        assertTrue(playResult.renderNowBar)
    }

    @Test
    fun playbackActionsStartServiceWhenTransportServiceIsMissing() {
        val player = FakePlaybackGateway(connected = false)
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(player)

        viewModel.skipToPrevious()
        viewModel.skipToNext()
        val result = viewModel.playTrackList(emptyList(), 0)

        assertEquals(
            listOf(
                "start:app.yukine.action.PREVIOUS",
                "start:app.yukine.action.NEXT"
            ),
            player.calls
        )
        assertEquals("Playback service is not connected", result.status)
    }

    @Test
    fun playbackModeActionsUpdateGateway() {
        val player = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(player)

        viewModel.togglePlayback(snapshotWithTrack(playing = true), emptyList())
        viewModel.togglePlayback(snapshotWithTrack(playing = false), emptyList())
        viewModel.toggleShuffle(snapshotWithTrack())
        viewModel.cycleRepeat()
        viewModel.cycleBottomPlaybackMode(
            PlaybackStateSnapshot(
                null,
                -1,
                0,
                0L,
                0L,
                false,
                false,
                "",
                true,
                EchoPlaybackService.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
        )

        assertEquals(
            listOf(
                "pause",
                "start:null",
                "play",
                "shuffle:true",
                "cycleRepeat",
                "shuffle:false",
                "repeat:1"
            ),
            player.calls
        )
    }

    private fun snapshotWithTrack(
        playing: Boolean = false,
        positionMs: Long = 0L
    ): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3"),
            0,
            1,
            positionMs,
            180_000L,
            playing,
            false,
            "",
            false,
            EchoPlaybackService.REPEAT_ALL,
            1.0f,
            1.0f,
            0L
        )
    }

    private class FakeGateway : NowPlayingGateway {
        val calls = ArrayList<String>()

        override fun playPause() {
            calls.add("playPause")
        }

        override fun next() {
            calls.add("next")
        }

        override fun previous() {
            calls.add("previous")
        }

        override fun seekTo(positionMs: Long) {
            calls.add("seek:$positionMs")
        }

        override fun toggleFavorite() {
            calls.add("favorite")
        }

        override fun toggleShuffle() {
            calls.add("shuffle")
        }

        override fun cycleRepeatMode() {
            calls.add("repeat")
        }

        override fun statusMessage(key: String): String {
            return if (key == "no.track.selected") "No track" else key
        }
    }

    private class FakePlaybackGateway(
        private var connected: Boolean = true
    ) : NowPlayingPlaybackGateway {
        val calls = ArrayList<String>()
        var queue: List<Track> = emptyList()
        private var snapshot = PlaybackStateSnapshot.empty()

        override fun serviceConnected(): Boolean = connected

        override fun startPlaybackService(action: String?) {
            calls.add("start:$action")
        }

        override fun snapshot(): PlaybackStateSnapshot? = snapshot

        override fun queueSnapshot(): List<Track> = queue

        override fun skipToPrevious() {
            calls.add("previous")
        }

        override fun skipToNext() {
            calls.add("next")
        }

        override fun seekTo(positionMs: Long) {
            calls.add("seek:$positionMs")
        }

        override fun removeTracksById(trackIds: Set<Long>) {
            calls.add("remove:${trackIds.joinToString(",")}")
        }

        override fun clearQueue() {
            calls.add("clear")
        }

        override fun replaceQueuedTrack(updated: Track) {
            calls.add("replace:${updated.id}")
        }

        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
            calls.add("replaceById:$oldTrackId:${updated.id}")
        }

        override fun retainTracksById(trackIds: Set<Long>) {
            calls.add("retain:${trackIds.joinToString(",")}")
        }

        override fun precacheTrack(track: Track) {
            calls.add("precache:${track.id}")
        }

        override fun appendToQueue(tracks: List<Track>) {
            calls.add("append:${tracks.size}")
        }

        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
            calls.add("replaceCurrent:${track.id}:$positionMs")
        }

        override fun startSleepTimerMinutes(minutes: Int) {
            calls.add("sleep:$minutes")
        }

        override fun cancelSleepTimer() {
            calls.add("cancelSleep")
        }

        override fun playQueue(tracks: List<Track>, index: Int) {
            calls.add("playQueue:${tracks.size}:$index")
        }

        override fun pause() {
            calls.add("pause")
        }

        override fun play() {
            calls.add("play")
        }

        override fun setShuffleEnabled(enabled: Boolean) {
            calls.add("shuffle:$enabled")
        }

        override fun cycleRepeatMode() {
            calls.add("cycleRepeat")
        }

        override fun setRepeatMode(repeatMode: Int) {
            calls.add("repeat:$repeatMode")
        }

        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
            calls.add("move:$fromIndex:$toIndex")
        }
    }
}
