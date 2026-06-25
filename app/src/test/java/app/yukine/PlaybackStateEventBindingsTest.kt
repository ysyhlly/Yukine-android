package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackStateEventBindingsTest {
    @Test
    fun forwardsPlaybackStateCallbacksToBoundActions() {
        val calls = mutableListOf<String>()
        val track = Track(7L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:7")
        val snapshot = PlaybackStateSnapshot(
            track,
            0,
            1,
            250L,
            1000L,
            true,
            false,
            "error",
            false,
            0,
            1.25f,
            0.8f,
            0L
        )
        var selectedTab = MainRoutes.TAB_NOW
        var currentLyricsTrackId = 42L
        var savedSpeed = 0f
        var savedVolume = 0f
        var loadedTrack: Track? = null
        var dashboardSnapshot: PlaybackStateSnapshot? = null
        var preResolvedSnapshot: PlaybackStateSnapshot? = null
        var recoveredSnapshot: PlaybackStateSnapshot? = null

        val listener = PlaybackStateEventBindings(
            selectedTabProvider = SettingsSelectedTabProvider { selectedTab },
            currentLyricsTrackIdProvider = CurrentLyricsTrackIdProvider { currentLyricsTrackId },
            playbackSettingsSaver = PlaybackSettingsSaver { speed, volume ->
                savedSpeed = speed
                savedVolume = volume
                calls += "settings"
            },
            loadLyricsAction = PlaybackTrackAction {
                loadedTrack = it
                calls += "lyrics"
            },
            loadCollectionsAction = Runnable { calls += "collections" },
            renderNowBarAction = Runnable { calls += "nowbar" },
            updateHomeDashboardPlaybackAction = PlaybackSnapshotAction {
                dashboardSnapshot = it
                calls += "dashboard"
            },
            renderSelectedTabAction = Runnable { calls += "render" },
            updateNowPlayingContentAction = Runnable { calls += "nowContent" },
            preResolveNextStreamingTrackAction = PlaybackSnapshotAction {
                preResolvedSnapshot = it
                calls += "preResolve"
            },
            recoverStreamingBufferingAction = PlaybackSnapshotAction {
                recoveredSnapshot = it
                calls += "recover"
            },
            resolveCurrentStreamingTrackAction = ResolveCurrentStreamingTrackAction {
                calls += "resolveCurrent"
                true
            },
            statusSink = SettingsStatusSink { calls += "status:$it" }
        )

        listener.savePlaybackSettings(1.25f, 0.8f)
        listener.loadLyrics(track)
        listener.loadCollections()
        listener.renderNowBar()
        listener.updateHomeDashboardPlayback(snapshot)
        listener.renderSelectedTab()
        listener.updateNowPlayingContent()
        listener.preResolveNextStreamingTrack(snapshot)
        listener.recoverStreamingBuffering(snapshot)
        listener.setStatus("error")

        assertEquals(MainRoutes.TAB_NOW, listener.selectedTab())
        assertEquals(42L, listener.currentLyricsTrackId())
        assertEquals(1.25f, savedSpeed, 0.0f)
        assertEquals(0.8f, savedVolume, 0.0f)
        assertSame(track, loadedTrack)
        assertSame(snapshot, dashboardSnapshot)
        assertSame(snapshot, preResolvedSnapshot)
        assertSame(snapshot, recoveredSnapshot)
        assertEquals(
            listOf(
                "settings",
                "lyrics",
                "collections",
                "nowbar",
                "dashboard",
                "render",
                "nowContent",
                "preResolve",
                "recover",
                "status:error"
            ),
            calls
        )

        selectedTab = MainRoutes.TAB_LIBRARY
        currentLyricsTrackId = 9L
        assertEquals(MainRoutes.TAB_LIBRARY, listener.selectedTab())
        assertEquals(9L, listener.currentLyricsTrackId())
    }
}
