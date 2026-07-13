package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainHomeDashboardRenderListenerTest {
    @Test
    fun delegatesHomeDashboardActionsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val track = homeTrack(1L)
        val listener = listener(calls)

        listener.openLibraryMode(LibraryGrouping.ALBUMS)
        listener.continuePlayback(track)
        listener.openNowPlaying()
        listener.playTrack(track)
        listener.refreshLibrary()
        listener.openQueue()
        listener.shuffleAll(listOf(homeTrack(2L)))
        listener.openStreaming()
        listener.openCollections()
        listener.openSearch()
        listener.playDailyRecommendations()
        listener.playHeartbeatRecommendations()

        assertEquals(
            listOf(
                "library:${LibraryGrouping.ALBUMS}",
                "continue:1",
                "now",
                "play:1:0",
                "refresh",
                "queue",
                "play:1:0",
                "streaming",
                "collections",
                "search",
                "daily",
                "heartbeat"
            ),
            calls
        )
    }

    @Test
    fun shuffleAllDoesNothingWhenLibraryIsEmpty() {
        val calls = mutableListOf<String>()
        listener(calls).shuffleAll(emptyList())

        assertTrue(calls.isEmpty())
    }

    private fun listener(
        calls: MutableList<String>
    ): MainHomeDashboardRenderListener =
        MainHomeDashboardRenderListener(
            libraryModeOpener = MainHomeDashboardRenderListener.LibraryModeOpener { calls += "library:$it" },
            playbackContinuer = MainHomeDashboardRenderListener.PlaybackContinuer { calls += "continue:${it?.id}" },
            nowPlayingOpener = MainHomeDashboardRenderListener.NowPlayingOpener { calls += "now" },
            trackListPlayer = MainHomeDashboardRenderListener.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            libraryRefresher = MainHomeDashboardRenderListener.LibraryRefresher { calls += "refresh" },
            queueOpener = MainHomeDashboardRenderListener.QueueOpener { calls += "queue" },
            streamingOpener = MainHomeDashboardRenderListener.StreamingOpener { calls += "streaming" },
            collectionsOpener = MainHomeDashboardRenderListener.CollectionsOpener { calls += "collections" },
            searchOpener = MainHomeDashboardRenderListener.SearchOpener { calls += "search" },
            dailyRecommendationsPlayer = MainHomeDashboardRenderListener.DailyRecommendationsPlayer { calls += "daily" },
            heartbeatRecommendationsPlayer = MainHomeDashboardRenderListener.HeartbeatRecommendationsPlayer { calls += "heartbeat" }
        )
}

private fun homeTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
