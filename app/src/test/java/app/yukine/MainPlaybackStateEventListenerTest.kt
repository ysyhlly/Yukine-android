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
        var preResolveSnapshot: PlaybackStateSnapshot? = null
        var bufferingSnapshot: PlaybackStateSnapshot? = null
        val listener = listener(
            calls = calls,
            nextStreamingTrackPreResolver = {
                calls += "pre-resolve"
                preResolveSnapshot = it
            },
            streamingBufferingRecoveryHandler = {
                calls += "buffering"
                bufferingSnapshot = it
            }
        )

        assertEquals(42L, listener.currentLyricsTrackId())
        listener.savePlaybackSettings(1.25f, 0.5f)
        listener.loadLyrics(null)
        listener.loadCollections()
        listener.preResolveNextStreamingTrack(snapshot)
        listener.recoverStreamingBuffering(snapshot)
        assertEquals(true, listener.resolveCurrentStreamingTrackIfNeeded())
        listener.setStatus("status.playback.error")

        assertEquals(
            listOf(
                "save:1.25:0.5",
                "lyrics:null",
                "collections",
                "pre-resolve",
                "buffering",
                "resolve-current",
                "status:status.playback.error"
            ),
            calls
        )
        assertSame(snapshot, preResolveSnapshot)
        assertSame(snapshot, bufferingSnapshot)
    }

    @Test
    fun factoryCreatesPlaybackStateEventControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlaybackUiModule.provideMainPlaybackStateEventListenerFactory().create(
            MainPlaybackStateEventListener.CurrentLyricsTrackIdSource { -1L },
            MainPlaybackStateEventListener.PlaybackSettingsSaver { speed, volume ->
                calls += "save:$speed:$volume"
            },
            MainPlaybackStateEventListener.LyricsLoader { calls += "lyrics" },
            MainPlaybackStateEventListener.CollectionsLoader { calls += "collections" },
            MainPlaybackStateEventListener.NextStreamingTrackPreResolver { calls += "pre-resolve" },
            MainPlaybackStateEventListener.StreamingBufferingRecoveryHandler { calls += "buffering" },
            MainPlaybackStateEventListener.CurrentStreamingTrackResolver {
                calls += "resolve-current"
                false
            },
            MainPlaybackStateEventListener.StatusSink { calls += "status:$it" }
        )

        listener.savePlaybackSettings(1.0f, 1.0f)
        listener.setStatus("ready")

        assertEquals(listOf("save:1.0:1.0", "status:ready"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        nextStreamingTrackPreResolver: (PlaybackStateSnapshot) -> Unit = {},
        streamingBufferingRecoveryHandler: (PlaybackStateSnapshot) -> Unit = {}
    ): MainPlaybackStateEventListener =
        MainPlaybackStateEventListener(
            currentLyricsTrackIdSource = MainPlaybackStateEventListener.CurrentLyricsTrackIdSource { 42L },
            playbackSettingsSaver = MainPlaybackStateEventListener.PlaybackSettingsSaver { speed, volume ->
                calls += "save:$speed:$volume"
            },
            lyricsLoader = MainPlaybackStateEventListener.LyricsLoader { calls += "lyrics:${it?.id}" },
            collectionsLoader = MainPlaybackStateEventListener.CollectionsLoader { calls += "collections" },
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
