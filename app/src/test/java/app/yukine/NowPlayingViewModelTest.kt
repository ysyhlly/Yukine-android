package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    fun librarySourceCandidatesExposeAlternatesAndEmitAConfirmedSwitchEffect() {
        val viewModel = NowPlayingViewModel()
        val current = Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:current.mp3")
        val alternate = Track(8L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:alternate.flac")
        viewModel.bindSourceCandidatesProvider { listOf(current, alternate) }

        assertEquals(listOf(current, alternate), viewModel.sourceCandidatesFor(current))

        viewModel.switchLocalSource(current, alternate)
        val effect = viewModel.drainEffects().single() as NowPlayingEffect.SwitchLibrarySource

        assertEquals(current, effect.current)
        assertEquals(alternate, effect.replacement)
        assertTrue(effect.requestId > 0L)

        viewModel.switchLocalSource(current, current)
        assertEquals(emptyList<NowPlayingEffect>(), viewModel.drainEffects())
    }

    @Test
    fun latestManualSourceSwitchSupersedesEarlierPendingRequest() {
        val viewModel = NowPlayingViewModel()
        val current = Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:current.mp3")

        viewModel.switchSource(
            current,
            StreamingProviderName.QQ_MUSIC,
            "qq-song",
            StreamingAudioQuality.HIGH
        )
        val first = viewModel.drainEffects().single() as NowPlayingEffect.SwitchSource

        viewModel.switchSource(
            current,
            StreamingProviderName.NETEASE,
            "netease-song",
            StreamingAudioQuality.LOSSLESS
        )
        val latest = viewModel.drainEffects().single() as NowPlayingEffect.SwitchSource

        assertTrue(latest.requestId > first.requestId)
        assertFalse(viewModel.isLatestSourceSwitchRequest(first.requestId))
        assertTrue(viewModel.isLatestSourceSwitchRequest(latest.requestId))
    }

    @Test
    fun sourceSwitchEventsImmediatelyEmitTheirSwitchEffects() {
        val viewModel = NowPlayingViewModel()
        val current = Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:current.mp3")
        val alternate = Track(8L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:alternate.flac")

        viewModel.onEvent(
            NowPlayingEvent.SwitchSource(
                current,
                StreamingProviderName.QQ_MUSIC,
                "qq-song",
                StreamingAudioQuality.HIGH
            )
        )
        val streamingEffect = viewModel.drainEffects().single() as NowPlayingEffect.SwitchSource

        assertEquals(current, streamingEffect.track)
        assertEquals(StreamingProviderName.QQ_MUSIC, streamingEffect.provider)
        assertEquals("qq-song", streamingEffect.providerTrackId)

        viewModel.onEvent(NowPlayingEvent.SwitchLibrarySource(current, alternate))
        val libraryEffect = viewModel.drainEffects().single() as NowPlayingEffect.SwitchLibrarySource

        assertEquals(current, libraryEffect.current)
        assertEquals(alternate, libraryEffect.replacement)
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
    fun boundStateSourcesReactToPlaybackFavoritesLyricsAndLanguage() {
        val viewModel = NowPlayingViewModel()
        val readModel = FakePlaybackReadModel()
        val library = MutableStateFlow(LibraryStoreState())
        val lyrics = MutableStateFlow(LyricsState())
        val settings = MutableStateFlow(SettingsState())
        val track = Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:7")

        viewModel.bindStateSources(readModel, library, lyrics, settings)
        readModel.state.value = snapshotWithTrack(track = track)
        library.value = LibraryStoreState(favoriteTrackIds = setOf(7L))
        lyrics.value = LyricsState(trackId = 7L, loadedLineCount = 1, statusKind = LyricsStatusKind.LOADED)
        settings.value = SettingsState(
            preferences = SettingsPreferencesSnapshot(languageMode = AppLanguage.MODE_CHINESE)
        )

        assertEquals(7L, viewModel.uiState.value.trackId)
        assertTrue(viewModel.uiState.value.isFavorite)
        assertTrue(viewModel.uiState.value.lyrics.status.isNotBlank())
        assertEquals("Song", viewModel.uiState.value.trackTitle)
    }

    @Test
    fun stableNegativeTrackCanBeFavoritedAndAddedToPlaylist() {
        val gateway = FakeGateway()
        val track = Track(-42L, "Imported", "Artist", "Album", 180_000L, Uri.EMPTY, "document:content://song")
        val viewModel = NowPlayingViewModel()
        viewModel.bindGateway(gateway)
        viewModel.updateState(snapshotWithTrack(track = track), emptySet(), null)

        viewModel.onEvent(NowPlayingEvent.ToggleFavorite)
        viewModel.onEvent(NowPlayingEvent.AddToPlaylist)

        assertEquals(listOf("favorite"), gateway.calls)
        val effect = viewModel.drainEffects().single() as NowPlayingEffect.OpenAddToPlaylist
        assertEquals(-42L, effect.track.id)
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
        player.playbackSnapshot = snapshotWithTrack()
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
        viewModel.warmPlaybackTrack(track)
        viewModel.appendToQueue(listOf(track))
        viewModel.replaceCurrentTrackAndResume(track, 7_000L)
        viewModel.replaceCurrentSourceAndResume(8L, track, 7_000L)

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
                "replaceCurrent:9:7000",
                "replaceCurrentSource:8:9:7000"
            ),
            player.calls
        )
        assertEquals("Status: Queued", removeResult.status)
        assertTrue(removeResult.renderSelectedTab)
        assertEquals("Status", clearResult.status)
        assertEquals("Sleep timer set: 20 minutes", sleepResult.status)
        assertEquals("Sleep timer cancelled", cancelResult.status)
        assertFalse(playResult.renderSelectedTab)
    }

    @Test
    fun playbackTransportActionsRemainSemanticWhenServiceIsMissing() {
        val player = FakePlaybackGateway(connected = false)
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(player)

        viewModel.skipToPrevious()
        viewModel.skipToNext()
        val track = Track(9L, "Queued", "Artist", "Album", 120_000L, Uri.EMPTY, "file:queued.mp3")
        viewModel.replaceQueuedTrack(8L, track)
        viewModel.retainTracks(listOf(track))
        viewModel.warmPlaybackTrack(track)
        viewModel.appendToQueue(listOf(track))
        viewModel.replaceCurrentTrackAndResume(track, 7_000L)
        viewModel.replaceCurrentSourceAndResume(8L, track, 7_000L)
        val result = viewModel.playTrackList(emptyList(), 0)

        assertEquals(
            listOf(
                "previous",
                "next",
                "replaceById:8:9",
                "retain:9",
                "precache:9",
                "append:1",
                "replaceCurrent:9:7000",
                "replaceCurrentSource:8:9:7000"
            ),
            player.calls
        )
        assertEquals("Playback service is not connected", result.status)
    }

    @Test
    fun sameIdStreamingResolutionsCrossThePlaybackBoundaryAsOneBatch() {
        val player = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(player)

        viewModel.replaceQueuedTracks(
            linkedMapOf(
                10L to Track(10L, "Ten", "Artist", "Album", 1_000L, Uri.EMPTY, "streaming:ten"),
                11L to Track(11L, "Eleven", "Artist", "Album", 1_000L, Uri.EMPTY, "streaming:eleven")
            )
        )

        assertEquals(listOf("replaceBatch:10,11"), player.calls)
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
                PlaybackRepeatMode.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
        )

        assertEquals(
            listOf(
                "pause",
                "play",
                "shuffle:true",
                "cycleRepeat",
                "shuffle:false",
                "repeat:0"
            ),
            player.calls
        )
    }

    private fun snapshotWithTrack(
        playing: Boolean = false,
        positionMs: Long = 0L,
        track: Track = Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3")
    ): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            0,
            1,
            positionMs,
            track.durationMs,
            playing,
            false,
            "",
            false,
            PlaybackRepeatMode.REPEAT_ALL,
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
        var playbackSnapshot = PlaybackStateSnapshot.empty()

        override fun serviceConnected(): Boolean = connected

        override fun snapshot(): PlaybackStateSnapshot? = playbackSnapshot

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

        override fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri) {
            calls.add("artwork:$trackId:$artworkUri")
        }

        override fun replaceQueuedTracks(updated: List<Track>) {
            calls.add("replaceBatch:${updated.joinToString(",") { it.id.toString() }}")
        }

        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
            calls.add("replaceById:$oldTrackId:${updated.id}")
        }

        override fun retainTracksById(trackIds: Set<Long>) {
            calls.add("retain:${trackIds.joinToString(",")}")
        }

        override fun warmPlaybackTrack(track: Track) {
            calls.add("precache:${track.id}")
        }

        override fun appendToQueue(tracks: List<Track>) {
            calls.add("append:${tracks.size}")
        }

        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
            calls.add("replaceCurrent:${track.id}:$positionMs")
        }

        override fun replaceCurrentSourceAndResume(expectedTrackId: Long, track: Track, positionMs: Long) {
            calls.add("replaceCurrentSource:$expectedTrackId:${track.id}:$positionMs")
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

    private class FakePlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }
}
