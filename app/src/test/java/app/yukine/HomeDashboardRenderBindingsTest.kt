package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.HomeDashboardActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeDashboardRenderBindingsTest {
    @Test
    fun forwardsHomeDashboardActionsToBoundOperations() {
        val calls = mutableListOf<String>()
        val played = mutableListOf<List<Track>>()
        val first = track(1L)
        val second = track(2L)
        val actions = emptyActions()
        var publishedActions: HomeDashboardActions? = null
        val bindings = HomeDashboardRenderBindings(
            libraryModeOpener = LibraryModeOpener { calls += "mode:$it" },
            continuePlaybackAction = NullableTrackAction { calls += "continue:${it?.id ?: -1L}" },
            openNowPlayingAction = Runnable { calls += "now" },
            playTrackListAction = TrackListPlaybackAction { tracks, index ->
                played += tracks
                calls += "play:${tracks.size}:$index"
            },
            refreshLibraryAction = Runnable { calls += "refresh" },
            openQueueAction = Runnable { calls += "queue" },
            allTracksProvider = TrackListProvider { listOf(first, second) },
            openStreamingAction = Runnable { calls += "streaming" },
            openCollectionsAction = Runnable { calls += "collections" },
            dailyRecommendationsAction = StreamingRecommendationAction { calls += "daily:$it" },
            heartbeatRecommendationsAction = StreamingRecommendationAction { calls += "heartbeat:$it" },
            actionsSink = HomeDashboardActionsSink { publishedActions = it }
        )

        bindings.openLibraryMode(LibraryGrouping.ALBUMS)
        bindings.continuePlayback(first)
        bindings.openNowPlaying()
        bindings.playTrack(second)
        bindings.refreshLibrary()
        bindings.openQueue()
        bindings.shuffleAll()
        bindings.openStreaming()
        bindings.openCollections()
        bindings.playDailyRecommendations()
        bindings.playHeartbeatRecommendations()
        bindings.publishHomeDashboardActions(actions)

        assertEquals(
            listOf(
                "mode:albums",
                "continue:1",
                "now",
                "play:1:0",
                "refresh",
                "queue",
                "play:2:0",
                "streaming",
                "collections",
                "daily:${StreamingProviderName.NETEASE}",
                "heartbeat:${StreamingProviderName.NETEASE}"
            ),
            calls
        )
        assertEquals(listOf(second), played[0])
        assertEquals(setOf(first, second), played[1].toSet())
        assertSame(actions, publishedActions)
    }

    @Test
    fun shuffleAllIgnoresEmptyLibrary() {
        val calls = mutableListOf<String>()
        val bindings = HomeDashboardRenderBindings(
            libraryModeOpener = LibraryModeOpener { calls += "mode" },
            continuePlaybackAction = NullableTrackAction { calls += "continue" },
            openNowPlayingAction = Runnable { calls += "now" },
            playTrackListAction = TrackListPlaybackAction { _, _ -> calls += "play" },
            refreshLibraryAction = Runnable { calls += "refresh" },
            openQueueAction = Runnable { calls += "queue" },
            allTracksProvider = TrackListProvider { emptyList() },
            openStreamingAction = Runnable { calls += "streaming" },
            openCollectionsAction = Runnable { calls += "collections" },
            dailyRecommendationsAction = StreamingRecommendationAction { calls += "daily" },
            heartbeatRecommendationsAction = StreamingRecommendationAction { calls += "heartbeat" },
            actionsSink = HomeDashboardActionsSink { calls += "actions" }
        )

        bindings.shuffleAll()

        assertEquals(emptyList<String>(), calls)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

    private fun emptyActions(): HomeDashboardActions {
        return HomeDashboardActions(
            onOpenStat = emptyList(),
            onContinue = Runnable { },
            onOpenNowPlaying = Runnable { },
            onPlayRecent = emptyList(),
            onRefresh = Runnable { },
            onViewQueue = Runnable { },
            onShuffleAll = Runnable { },
            onRecentTabChanged = {},
            onDailyRecommend = Runnable { },
            onHeartbeatRecommend = Runnable { },
            onOpenCollections = Runnable { }
        )
    }
}
