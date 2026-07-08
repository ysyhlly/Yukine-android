package app.yukine

import android.net.Uri
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
    fun factoryCreatesNowPlayingStateControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlaybackUiModule.provideMainNowPlayingStateListenerFactory().create(
            NowPlayingStoresReadySource { false },
            NowPlayingPlaybackSnapshotSource { snapshot() },
            NowPlayingFavoriteIdsSource { setOf(7L) },
            NowPlayingLyricsStateSource { null },
            NowPlayingLanguageModeSource { AppLanguage.MODE_SYSTEM },
            NowPlayingQueueVisibilitySource { true },
            NowPlayingFloatingLyricsSink { title, _, _, _, activeLine -> calls += "floating:$title:$activeLine" },
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
        queueVisible: Boolean = true
    ): MainNowPlayingStateListener =
        MainNowPlayingStateListener(
            storesReadySource = NowPlayingStoresReadySource { storesReady },
            playbackSnapshotSource = NowPlayingPlaybackSnapshotSource { snapshot },
            favoriteIdsSource = NowPlayingFavoriteIdsSource { favoriteIds },
            lyricsStateSource = NowPlayingLyricsStateSource { lyricsState },
            languageModeSource = NowPlayingLanguageModeSource { languageMode },
            queueVisibilitySource = NowPlayingQueueVisibilitySource { queueVisible },
            floatingLyricsSink = NowPlayingFloatingLyricsSink { title, artist, coverUri, playing, activeLine ->
                calls += "floating:$title:$artist:${coverUri.orEmpty()}:$playing:$activeLine"
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
