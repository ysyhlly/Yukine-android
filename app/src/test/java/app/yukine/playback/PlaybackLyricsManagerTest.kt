package app.yukine.playback

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.FloatingLyricsPublisher
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

    private fun snapshot(track: Track): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            0,
            1,
            0L,
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

    private class FakeStateProvider : PlaybackLyricsManager.StateProvider {
        override fun isAppVisible(): Boolean = true

        override fun currentTrack(): Track? = null

        override fun isPlaying(): Boolean = false

        override fun isPreparing(): Boolean = false
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
