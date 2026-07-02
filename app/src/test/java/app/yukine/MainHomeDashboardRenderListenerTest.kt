package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.HomeDashboardActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainHomeDashboardRenderListenerTest {
    @Test
    fun delegatesHomeDashboardActionsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val track = homeTrack(1L)
        val actions = homeActions()
        val listener = listener(calls, allTracks = listOf(homeTrack(2L)))

        listener.openLibraryMode(LibraryGrouping.ALBUMS)
        listener.continuePlayback(track)
        listener.openNowPlaying()
        listener.playTrack(track)
        listener.refreshLibrary()
        listener.openQueue()
        listener.shuffleAll()
        listener.openStreaming()
        listener.openCollections()
        listener.openSearch()
        listener.playDailyRecommendations()
        listener.playHeartbeatRecommendations()
        listener.publishHomeDashboardActions(actions)

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
                "heartbeat",
                "actions"
            ),
            calls
        )
    }

    @Test
    fun shuffleAllDoesNothingWhenLibraryIsEmpty() {
        val calls = mutableListOf<String>()
        listener(calls, allTracks = emptyList()).shuffleAll()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun factoryCreatesHomeDashboardRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = app.yukine.di.ShellModule.provideMainHomeDashboardRenderListenerFactory().create(
            MainHomeDashboardRenderListener.LibraryModeOpener { calls += "library:$it" },
            MainHomeDashboardRenderListener.PlaybackContinuer { calls += "continue:${it?.id}" },
            MainHomeDashboardRenderListener.NowPlayingOpener { calls += "now" },
            MainHomeDashboardRenderListener.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            MainHomeDashboardRenderListener.LibraryRefresher { calls += "refresh" },
            MainHomeDashboardRenderListener.QueueOpener { calls += "queue" },
            MainHomeDashboardRenderListener.AllTracksSource { listOf(homeTrack(3L)) },
            MainHomeDashboardRenderListener.StreamingOpener { calls += "streaming" },
            MainHomeDashboardRenderListener.CollectionsOpener { calls += "collections" },
            MainHomeDashboardRenderListener.SearchOpener { calls += "search" },
            MainHomeDashboardRenderListener.DailyRecommendationsPlayer { calls += "daily" },
            MainHomeDashboardRenderListener.HeartbeatRecommendationsPlayer { calls += "heartbeat" },
            MainHomeDashboardRenderListener.ActionsPublisher { calls += "actions" }
        )

        listener.playTrack(homeTrack(4L))
        listener.shuffleAll()
        listener.publishHomeDashboardActions(homeActions())

        assertEquals(listOf("play:1:0", "play:1:0", "actions"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        allTracks: List<Track> = emptyList()
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
            allTracksSource = MainHomeDashboardRenderListener.AllTracksSource { allTracks },
            streamingOpener = MainHomeDashboardRenderListener.StreamingOpener { calls += "streaming" },
            collectionsOpener = MainHomeDashboardRenderListener.CollectionsOpener { calls += "collections" },
            searchOpener = MainHomeDashboardRenderListener.SearchOpener { calls += "search" },
            dailyRecommendationsPlayer = MainHomeDashboardRenderListener.DailyRecommendationsPlayer { calls += "daily" },
            heartbeatRecommendationsPlayer = MainHomeDashboardRenderListener.HeartbeatRecommendationsPlayer { calls += "heartbeat" },
            actionsPublisher = MainHomeDashboardRenderListener.ActionsPublisher { calls += "actions" }
        )

    private fun homeActions(): HomeDashboardActions =
        HomeDashboardActions(
            onOpenStat = emptyList(),
            onContinue = Runnable {},
            onOpenNowPlaying = Runnable {},
            onPlayRecent = emptyList(),
            onRefresh = Runnable {},
            onViewQueue = Runnable {},
            onShuffleAll = Runnable {},
            onRecentTabChanged = { _ -> },
            onDailyRecommend = Runnable {},
            onHeartbeatRecommend = Runnable {},
            onOpenCollections = Runnable {},
            onConnectStreaming = Runnable {},
            onSearch = Runnable {}
        )
}

private fun homeTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
