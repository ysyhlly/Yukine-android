package app.yukine.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.FloatingLyricsPublisher
import app.yukine.LiveLyricsNotificationService
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackLyricsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackLyricsManagerTest {
    @Test
    fun statusBarLyricsSettingChangeRefreshesSessionAndNotificationFromLyricsOwner() {
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.setStatusBarLyricsEnabled(false)
        manager.setStatusBarLyricsEnabled(false)

        assertEquals(1, bridge.refreshPlaybackSessionCalls)
        assertEquals(listOf(true), bridge.notificationForces)
    }

    @Test
    fun systemMediaLyricTitleSettingChangeRefreshesSessionAndNotificationOnce() {
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge().apply { notificationWorthy = true }
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.setSystemMediaLyricsTitleEnabled(true)
        manager.setSystemMediaLyricsTitleEnabled(true)

        assertEquals(1, bridge.refreshPlaybackSessionCalls)
        assertEquals(listOf(true), bridge.notificationForces)
    }

    @Test
    fun releaseStopsFutureStatusBarSettingRefreshes() {
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.release()
        manager.setStatusBarLyricsEnabled(false)

        assertEquals(0, bridge.refreshPlaybackSessionCalls)
        assertEquals(emptyList<Boolean>(), bridge.notificationForces)
    }

    @Test
    fun releaseStopsFutureFloatingLyricsSync() {
        FloatingLyricsPublisher.clear()
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.release()
        manager.syncFloatingLyricsPlaybackState(snapshot(track()))

        assertEquals("", FloatingLyricsPublisher.snapshot().trackTitle)
        assertEquals("", FloatingLyricsPublisher.snapshot().artist)
    }

    @Test
    fun floatingLyricsSyncPublishesSnapshotTrackAndClearsStaleLyricLine() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Old Track", "Old Artist", null, true, "old line")
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.syncFloatingLyricsPlaybackState(snapshot(track()))

        val state = FloatingLyricsPublisher.snapshot()
        assertEquals("Track 1", state.trackTitle)
        assertEquals("Artist", state.artist)
        assertEquals(true, state.playing)
        assertEquals("", state.activeLine)
    }

    @Test
    fun releaseStopsLiveLyricsServiceOnlyOnce() {
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val manager = PlaybackLyricsManager(
            context,
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        manager.release()
        manager.release()

        assertEquals(
            listOf(LiveLyricsNotificationService::class.java.name),
            context.stoppedServices
        )
    }

    @Test
    fun liveLyricsCloudDoesNotStartWhileAppIsVisible() {
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val provider = FakeStateProvider(
            appVisible = true,
            currentTrack = track(),
            playing = true
        )
        val manager = PlaybackLyricsManager(
            context,
            provider,
            FakeNotificationBridge()
        )

        manager.setStatusBarLyricsEnabled(false)
        manager.setStatusBarLyricsEnabled(true)

        assertEquals(emptyList<String>(), context.startedServices)
        assertEquals(
            listOf(
                LiveLyricsNotificationService::class.java.name,
                LiveLyricsNotificationService::class.java.name
            ),
            context.stoppedServices
        )
    }

    @Test
    fun liveLyricsCloudStartsOnlyWhenPlaybackContinuesInBackground() {
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val provider = FakeStateProvider(
            appVisible = false,
            currentTrack = track(),
            playing = true
        )
        val manager = PlaybackLyricsManager(
            context,
            provider,
            FakeNotificationBridge()
        )

        manager.setStatusBarLyricsEnabled(false)
        manager.setStatusBarLyricsEnabled(true)

        assertEquals(
            listOf(LiveLyricsNotificationService::class.java.name),
            context.startedServices
        )
    }

    @Test
    fun floatingLyricsNotificationRefreshUsesNotificationBridgeWorthiness() {
        FloatingLyricsPublisher.clear()
        val provider = FakeStateProvider()
        val bridge = FakeNotificationBridge()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider,
            bridge
        )

        manager.bind()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "first line")

        assertEquals(emptyList<Boolean>(), bridge.notificationForces)

        bridge.notificationWorthy = true
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "second line")

        assertEquals(listOf(false), bridge.notificationForces)
        assertEquals(1, bridge.refreshPlaybackSessionCalls)

        manager.release()
    }

    @Test
    fun movingPlaybackToBackgroundImmediatelyStartsLiveLyricsAndRefreshesNotification() {
        FloatingLyricsPublisher.clear()
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val provider = FakeStateProvider(
            appVisible = true,
            currentTrack = track(),
            playing = true
        )
        val bridge = FakeNotificationBridge().apply { notificationWorthy = true }
        val manager = PlaybackLyricsManager(context, provider, bridge)

        provider.appVisible = false
        manager.onAppVisibilityChanged()

        assertEquals(
            listOf(LiveLyricsNotificationService::class.java.name),
            context.startedServices
        )
        assertEquals(listOf(true), bridge.notificationForces)
        assertEquals(1, bridge.refreshPlaybackSessionCalls)

        manager.release()
    }

    @Test
    fun movingPlaybackBackToForegroundImmediatelyStopsLiveLyrics() {
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val provider = FakeStateProvider(
            appVisible = false,
            currentTrack = track(),
            playing = true
        )
        val manager = PlaybackLyricsManager(context, provider, FakeNotificationBridge())

        manager.onAppVisibilityChanged()
        provider.appVisible = true
        manager.onAppVisibilityChanged()

        assertEquals(
            listOf(LiveLyricsNotificationService::class.java.name),
            context.startedServices
        )
        assertEquals(
            listOf(LiveLyricsNotificationService::class.java.name),
            context.stoppedServices
        )

        manager.release()
    }

    @Test
    fun rapidBackgroundLyricLinesDoNotDropTheLatestNotificationRefresh() {
        FloatingLyricsPublisher.clear()
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val provider = FakeStateProvider(
            appVisible = false,
            currentTrack = track(),
            playing = true
        )
        val bridge = FakeNotificationBridge().apply { notificationWorthy = true }
        val manager = PlaybackLyricsManager(context, provider, bridge)

        manager.bind()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "first line")
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "latest line")

        assertEquals(listOf(false, false), bridge.notificationForces)
        assertEquals(2, bridge.refreshPlaybackSessionCalls)
        assertEquals("latest line", manager.notificationLyricText(track()))

        manager.release()
    }

    @Test
    fun serviceProgressAdvancesPublishedLyricsTimelineAfterActivityStopsPublishing() {
        FloatingLyricsPublisher.clear()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        FloatingLyricsPublisher.update(
            trackId = 1L,
            trackTitle = "Track 1",
            artist = "Artist",
            albumArtUri = null,
            playing = true,
            activeLine = "stale",
            lyrics = listOf(
                LyricsLine(1_000L, "first"),
                LyricsLine(2_000L, "second")
            ),
            lyricsOffsetMs = 250L
        )
        manager.syncFloatingLyricsPlaybackState(snapshot(track(), positionMs = 0L))

        assertEquals("first", FloatingLyricsPublisher.snapshot().activeLine)

        manager.syncFloatingLyricsPlaybackState(snapshot(track(), positionMs = 1_800L))

        assertEquals("second", FloatingLyricsPublisher.snapshot().activeLine)

        manager.release()
        FloatingLyricsPublisher.clear()
    }

    @Test
    fun serviceProgressDoesNotReuseLyricsTimelineForAnotherTrack() {
        FloatingLyricsPublisher.clear()
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )
        FloatingLyricsPublisher.update(
            trackId = 1L,
            trackTitle = "Track 1",
            artist = "Artist",
            albumArtUri = null,
            playing = true,
            activeLine = "first",
            lyrics = listOf(LyricsLine(0L, "first")),
            lyricsOffsetMs = 0L
        )
        val anotherTrack = Track(
            2L,
            "Track 2",
            "Artist",
            "Album",
            180_000L,
            Uri.EMPTY,
            "local-2"
        )

        manager.syncFloatingLyricsPlaybackState(snapshot(anotherTrack, positionMs = 2_000L))

        assertEquals("", FloatingLyricsPublisher.snapshot().activeLine)

        manager.release()
        FloatingLyricsPublisher.clear()
    }

    @Test
    fun notificationLyricTextReturnsSanitizedLineForCurrentTrack() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "  first   line  \n\n second line \n third line")
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        assertEquals("first line\nsecond line", manager.notificationLyricText(track()))
    }

    @Test
    fun notificationLyricTextReturnsEmptyForMismatchedTrack() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Other Track", "Artist", null, true, "first line")
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        assertEquals("", manager.notificationLyricText(track()))
    }

    @Test
    fun notificationLyricTextReturnsEmptyWhenStatusBarLyricsDisabled() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "first line")
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        manager.setStatusBarLyricsEnabled(false)

        assertEquals("", manager.notificationLyricText(track()))
    }

    @Test
    fun systemMediaTitleLyricRemainsAvailableWhenNotificationLyricsAreDisabled() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "first line\nsecond line")
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        manager.setStatusBarLyricsEnabled(false)
        manager.setSystemMediaLyricsTitleEnabled(true)

        assertEquals("first line\nsecond line", manager.systemMediaTitleLyricText(track()))
    }

    @Test
    fun notificationLyricTextReturnsEmptyAfterRelease() {
        FloatingLyricsPublisher.clear()
        FloatingLyricsPublisher.update("Track 1", "Artist", null, true, "first line")
        val context = FakeContext(ApplicationProvider.getApplicationContext())
        val manager = PlaybackLyricsManager(
            context,
            FakeStateProvider(),
            FakeNotificationBridge()
        )

        manager.release()

        assertEquals("", manager.notificationLyricText(track()))
    }

    private fun track(): Track {
        return Track(
            1L,
            "Track 1",
            "Artist",
            "Album",
            180_000L,
            Uri.EMPTY,
            "local-1"
        )
    }

    private fun snapshot(track: Track, positionMs: Long = 0L): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            0,
            1,
            positionMs,
            track.durationMs,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
    }

    private class FakeStateProvider(
        var appVisible: Boolean = true,
        var currentTrack: Track? = null,
        var playing: Boolean = false,
        var preparing: Boolean = false
    ) : PlaybackLyricsManager.StateProvider {
        override fun isAppVisible(): Boolean = appVisible

        override fun currentTrack(): Track? = currentTrack

        override fun isPlaying(): Boolean = playing

        override fun isPreparing(): Boolean = preparing
    }

    private class FakeContext(base: Context) : ContextWrapper(base) {
        val startedServices = mutableListOf<String>()
        val stoppedServices = mutableListOf<String>()

        override fun startForegroundService(service: Intent?): android.content.ComponentName? {
            service?.component?.className?.let(startedServices::add)
            return service?.component
        }

        override fun startService(service: Intent?): android.content.ComponentName? {
            service?.component?.className?.let(startedServices::add)
            return service?.component
        }

        override fun stopService(name: Intent?): Boolean {
            name?.component?.className?.let(stoppedServices::add)
            return true
        }
    }

    private class FakeNotificationBridge : PlaybackLyricsManager.NotificationBridge {
        var refreshPlaybackSessionCalls = 0
        var notificationWorthy = false
        val notificationForces = mutableListOf<Boolean>()

        override fun hasNotificationWorthyState(): Boolean = notificationWorthy

        override fun notifyMediaNotification(force: Boolean) {
            notificationForces += force
        }

        override fun refreshPlaybackSession() {
            refreshPlaybackSessionCalls++
        }
    }
}
