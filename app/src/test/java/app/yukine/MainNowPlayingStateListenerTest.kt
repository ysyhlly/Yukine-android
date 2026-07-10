package app.yukine

import android.net.Uri
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.LyricUiLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNowPlayingStateListenerTest {
    @Test
    fun delegatesNowPlayingStateInputsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val snapshot = snapshot()
        val lyricsState = LyricsState()
        val listener = listener(
            calls = calls,
            storesReady = true,
            snapshot = snapshot,
            favoriteIds = setOf(1L, 2L),
            lyricsState = lyricsState,
            languageMode = AppLanguage.MODE_ENGLISH,
            queueVisible = true
        )

        assertEquals(true, listener.storesReady())
        assertEquals(snapshot, listener.playbackSnapshot())
        assertEquals(setOf(1L, 2L), listener.favoriteIds())
        assertEquals(lyricsState, listener.lyricsState())
        assertEquals(AppLanguage.MODE_ENGLISH, listener.languageMode())
        assertEquals(true, listener.queueVisible())
        listener.syncQueueInputs()

        assertEquals(listOf("queue"), calls)
    }

    @Test
    fun publishesActiveFloatingLyricLine() {
        val calls = mutableListOf<String>()
        val listener = listener(calls)
        val state = NowPlayingUiState(
            trackTitle = "Song",
            artist = "Artist",
            coverUri = "cover",
            isPlaying = true,
            lyrics = LyricsUiState(
                lines = listOf(
                    LyricUiLine("first", active = false),
                    LyricUiLine("active", active = true)
                )
            )
        )

        listener.publishFloatingLyrics(state)

        assertEquals(listOf("floating:Song:Artist:cover:true:active"), calls)
    }

    @Test
    fun fallsBackToFirstLyricLineWhenNoneIsActive() {
        val calls = mutableListOf<String>()
        val listener = listener(calls)
        val state = NowPlayingUiState(
            trackTitle = "Song",
            lyrics = LyricsUiState(lines = listOf(LyricUiLine("first", active = false)))
        )

        listener.publishFloatingLyrics(state)

        assertEquals(listOf("floating:Song:::false:first"), calls)
    }

    @Test
    fun publishesMatchingLyricsTimelineForServiceSideProgressUpdates() {
        val calls = mutableListOf<String>()
        val lyrics = listOf(
            LyricsLine(1_000L, "first"),
            LyricsLine(2_000L, "second")
        )
        var publishedTrackId = -1L
        var publishedLines: List<LyricsLine> = emptyList()
        var publishedOffsetMs = 0L
        val listener = listener(
            calls = calls,
            lyricsState = LyricsState(trackId = 7L, lines = lyrics, offsetMs = 250L),
            onFloatingLyrics = { trackId, lines, offsetMs ->
                publishedTrackId = trackId
                publishedLines = lines
                publishedOffsetMs = offsetMs
            }
        )

        listener.publishFloatingLyrics(
            NowPlayingUiState(
                trackId = 7L,
                trackTitle = "Song",
                lyrics = LyricsUiState(lines = listOf(LyricUiLine("first", active = true)))
            )
        )

        assertEquals(7L, publishedTrackId)
        assertEquals(lyrics, publishedLines)
        assertEquals(250L, publishedOffsetMs)
    }

    @Test
    fun factoryCreatesNowPlayingStateControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlaybackUiModule.provideMainNowPlayingStateListenerFactory().create(
            NowPlayingStoresReadySource { false },
            NowPlayingPlaybackSnapshotSource { snapshot() },
            NowPlayingFavoriteIdsSource { setOf(7L) },
            NowPlayingLyricsStateSource { null },
            NowPlayingLanguageModeSource { AppLanguage.MODE_SYSTEM },
            NowPlayingQueueVisibilitySource { true },
            NowPlayingFloatingLyricsSink { _, title, _, _, _, activeLine, _, _ ->
                calls += "floating:$title:$activeLine"
            },
            NowPlayingQueueInputsSyncer { calls += "queue" }
        )

        assertEquals(false, listener.storesReady())
        assertEquals(setOf(7L), listener.favoriteIds())
        assertEquals(AppLanguage.MODE_SYSTEM, listener.languageMode())
        assertEquals(true, listener.queueVisible())
        listener.publishFloatingLyrics(
            NowPlayingUiState(
                trackTitle = "Song",
                lyrics = LyricsUiState(lines = listOf(LyricUiLine("line", active = true)))
            )
        )
        listener.syncQueueInputs()

        assertEquals(listOf("floating:Song:line", "queue"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        storesReady: Boolean = true,
        snapshot: PlaybackStateSnapshot = snapshot(),
        favoriteIds: Set<Long> = emptySet(),
        lyricsState: LyricsState? = null,
        languageMode: String = AppLanguage.MODE_SYSTEM,
        queueVisible: Boolean = true,
        onFloatingLyrics: ((Long, List<LyricsLine>, Long) -> Unit)? = null
    ): MainNowPlayingStateListener =
        MainNowPlayingStateListener(
            storesReadySource = NowPlayingStoresReadySource { storesReady },
            playbackSnapshotSource = NowPlayingPlaybackSnapshotSource { snapshot },
            favoriteIdsSource = NowPlayingFavoriteIdsSource { favoriteIds },
            lyricsStateSource = NowPlayingLyricsStateSource { lyricsState },
            languageModeSource = NowPlayingLanguageModeSource { languageMode },
            queueVisibilitySource = NowPlayingQueueVisibilitySource { queueVisible },
            floatingLyricsSink = NowPlayingFloatingLyricsSink { trackId, title, artist, coverUri, playing, activeLine, lines, offsetMs ->
                calls += "floating:$title:$artist:${coverUri.orEmpty()}:$playing:$activeLine"
                onFloatingLyrics?.invoke(trackId, lines, offsetMs)
            },
            queueInputsSyncer = NowPlayingQueueInputsSyncer { calls += "queue" }
        )

    companion object {
        private fun snapshot(): PlaybackStateSnapshot =
            PlaybackStateSnapshot(
                Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3"),
                0,
                1,
                0L,
                180_000L,
                true,
                false,
                "",
                false,
                EchoPlaybackService.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
    }
}
