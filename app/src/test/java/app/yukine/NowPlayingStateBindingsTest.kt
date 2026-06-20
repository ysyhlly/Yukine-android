package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingStateBindingsTest {
    @Test
    fun forwardsNowPlayingStateEdgesToActivityBindings() {
        val calls = mutableListOf<String>()
        val bindings = NowPlayingStateBindings(
            storesReadyProvider = NowPlayingStoresReadyProvider {
                calls += "ready"
                true
            },
            snapshotProvider = NowPlayingSnapshotProvider {
                calls += "snapshot"
                PlaybackStateSnapshot.empty()
            },
            favoriteIdsProvider = NowPlayingFavoriteIdsProvider {
                calls += "favorites"
                setOf(4L)
            },
            lyricsProvider = NowPlayingLyricsProvider {
                calls += "lyrics"
                null
            },
            languageProvider = NowPlayingLanguageProvider {
                calls += "language"
                AppLanguage.MODE_ENGLISH
            },
            floatingLyricsPublisher = NowPlayingFloatingLyricsPublisher {
                calls += "floating:${it.trackTitle}"
            },
            queueInputSynchronizer = NowPlayingQueueInputSynchronizer {
                calls += "queue"
            }
        )

        bindings.storesReady()
        bindings.playbackSnapshot()
        bindings.favoriteIds()
        bindings.lyricsState()
        bindings.languageMode()
        bindings.publishFloatingLyrics(NowPlayingUiState(trackTitle = "Song"))
        bindings.syncQueueInputs()

        assertEquals(
            listOf("ready", "snapshot", "favorites", "lyrics", "language", "floating:Song", "queue"),
            calls
        )
    }
}
