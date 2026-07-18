package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardActionAdapterTest {
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
        listener.nextTrack()
        listener.shuffleAll(listOf(homeTrack(2L)))
        listener.openStreaming()
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
                "next",
                "play:1:0",
                "streaming",
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
    ): HomeDashboardActionAdapter =
        HomeDashboardActionAdapter(
            libraryModeOpener = HomeDashboardActionAdapter.LibraryModeOpener { calls += "library:$it" },
            playbackContinuer = HomeDashboardActionAdapter.PlaybackContinuer { calls += "continue:${it?.id}" },
            nowPlayingOpener = HomeDashboardActionAdapter.NowPlayingOpener { calls += "now" },
            trackListPlayer = HomeDashboardActionAdapter.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            libraryRefresher = HomeDashboardActionAdapter.LibraryRefresher { calls += "refresh" },
            queueOpener = HomeDashboardActionAdapter.QueueOpener { calls += "queue" },
            streamingOpener = HomeDashboardActionAdapter.StreamingOpener { calls += "streaming" },
            searchOpener = HomeDashboardActionAdapter.SearchOpener { calls += "search" },
            dailyRecommendationsPlayer = HomeDashboardActionAdapter.DailyRecommendationsPlayer { calls += "daily" },
            heartbeatRecommendationsPlayer = HomeDashboardActionAdapter.HeartbeatRecommendationsPlayer { calls += "heartbeat" },
            nextTrackPlayer = HomeDashboardActionAdapter.NextTrackPlayer { calls += "next" }
        )
}

private fun homeTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
