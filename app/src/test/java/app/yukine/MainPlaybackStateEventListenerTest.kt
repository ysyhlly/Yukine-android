package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainPlaybackStateEventListenerTest {
    @Test
    fun delegatesPlaybackStateEventCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val snapshot = PlaybackStateSnapshot.empty()
        var homeDashboardSnapshot: PlaybackStateSnapshot? = null
        var preResolveSnapshot: PlaybackStateSnapshot? = null
        var bufferingSnapshot: PlaybackStateSnapshot? = null
        val listener = listener(
            calls = calls,
            homeDashboardPlaybackUpdater = {
                calls += "home"
                homeDashboardSnapshot = it
            },
            nextStreamingTrackPreResolver = {
                calls += "pre-resolve"
                preResolveSnapshot = it
            },
            streamingBufferingRecoveryHandler = {
                calls += "buffering"
                bufferingSnapshot = it
            }
        )

        assertEquals("queue", listener.selectedTab())
        assertEquals(true, listener.queueVisible())
        assertEquals(42L, listener.currentLyricsTrackId())
        listener.savePlaybackSettings(1.25f, 0.5f)
        listener.loadLyrics(null)
        listener.loadCollections()
        listener.renderNowBar()
        listener.updateHomeDashboardPlayback(snapshot)
        listener.renderSelectedTab()
        listener.updateNowPlayingContent()
        listener.preResolveNextStreamingTrack(snapshot)
        listener.recoverStreamingBuffering(snapshot)
        assertEquals(true, listener.resolveCurrentStreamingTrackIfNeeded())
        listener.setStatus("status.playback.error")

        assertEquals(
            listOf(
                "save:1.25:0.5",
                "lyrics:null",
                "collections",
                "now-bar",
                "home",
                "selected-tab",
                "now-playing",
                "pre-resolve",
                "buffering",
                "resolve-current",
                "status:status.playback.error"
            ),
            calls
        )
        assertSame(snapshot, homeDashboardSnapshot)
        assertSame(snapshot, preResolveSnapshot)
        assertSame(snapshot, bufferingSnapshot)
    }

    @Test
    fun factoryCreatesPlaybackStateEventControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlaybackUiModule.provideMainPlaybackStateEventListenerFactory().create(
            MainPlaybackStateEventListener.SelectedTabSource { "library" },
            MainPlaybackStateEventListener.QueueVisibilitySource { true },
            MainPlaybackStateEventListener.CurrentLyricsTrackIdSource { -1L },
            MainPlaybackStateEventListener.PlaybackSettingsSaver { speed, volume ->
                calls += "save:$speed:$volume"
            },
            MainPlaybackStateEventListener.LyricsLoader { calls += "lyrics" },
            MainPlaybackStateEventListener.CollectionsLoader { calls += "collections" },
            MainPlaybackStateEventListener.NowBarRenderer { calls += "now-bar" },
            MainPlaybackStateEventListener.HomeDashboardPlaybackUpdater { calls += "home" },
            MainPlaybackStateEventListener.SelectedTabRenderer { calls += "selected-tab" },
            MainPlaybackStateEventListener.NowPlayingContentUpdater { calls += "now-playing" },
            MainPlaybackStateEventListener.NextStreamingTrackPreResolver { calls += "pre-resolve" },
            MainPlaybackStateEventListener.StreamingBufferingRecoveryHandler { calls += "buffering" },
            MainPlaybackStateEventListener.CurrentStreamingTrackResolver {
                calls += "resolve-current"
                false
            },
            MainPlaybackStateEventListener.StatusSink { calls += "status:$it" }
        )

        assertEquals("library", listener.selectedTab())
        assertEquals(true, listener.queueVisible())
        listener.savePlaybackSettings(1.0f, 1.0f)
        listener.setStatus("ready")

        assertEquals(listOf("save:1.0:1.0", "status:ready"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        homeDashboardPlaybackUpdater: (PlaybackStateSnapshot) -> Unit = {},
        nextStreamingTrackPreResolver: (PlaybackStateSnapshot) -> Unit = {},
        streamingBufferingRecoveryHandler: (PlaybackStateSnapshot) -> Unit = {}
    ): MainPlaybackStateEventListener =
        MainPlaybackStateEventListener(
            selectedTabSource = MainPlaybackStateEventListener.SelectedTabSource { "queue" },
            queueVisibilitySource = MainPlaybackStateEventListener.QueueVisibilitySource { true },
            currentLyricsTrackIdSource = MainPlaybackStateEventListener.CurrentLyricsTrackIdSource { 42L },
            playbackSettingsSaver = MainPlaybackStateEventListener.PlaybackSettingsSaver { speed, volume ->
                calls += "save:$speed:$volume"
            },
            lyricsLoader = MainPlaybackStateEventListener.LyricsLoader { calls += "lyrics:${it?.id}" },
            collectionsLoader = MainPlaybackStateEventListener.CollectionsLoader { calls += "collections" },
            nowBarRenderer = MainPlaybackStateEventListener.NowBarRenderer { calls += "now-bar" },
            homeDashboardPlaybackUpdater = MainPlaybackStateEventListener.HomeDashboardPlaybackUpdater(
                homeDashboardPlaybackUpdater
            ),
            selectedTabRenderer = MainPlaybackStateEventListener.SelectedTabRenderer { calls += "selected-tab" },
            nowPlayingContentUpdater = MainPlaybackStateEventListener.NowPlayingContentUpdater {
                calls += "now-playing"
            },
            nextStreamingTrackPreResolver = MainPlaybackStateEventListener.NextStreamingTrackPreResolver(
                nextStreamingTrackPreResolver
            ),
            streamingBufferingRecoveryHandler = MainPlaybackStateEventListener.StreamingBufferingRecoveryHandler(
                streamingBufferingRecoveryHandler
            ),
            currentStreamingTrackResolver = MainPlaybackStateEventListener.CurrentStreamingTrackResolver {
                calls += "resolve-current"
                true
            },
            statusSink = MainPlaybackStateEventListener.StatusSink { calls += "status:$it" }
        )
}
