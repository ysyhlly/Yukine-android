package app.yukine.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        val manager = PlaybackLyricsManager(
            ApplicationProvider.getApplicationContext<Context>(),
            provider
        )

        manager.setStatusBarLyricsEnabled(false)
        manager.setStatusBarLyricsEnabled(false)

        assertEquals(1, provider.refreshPlaybackSessionCalls)
        assertEquals(listOf(true), provider.notificationForces)
    }

    private class FakeStateProvider : PlaybackLyricsManager.StateProvider {
        var refreshPlaybackSessionCalls = 0
        val notificationForces = mutableListOf<Boolean>()

        override fun hasNotificationWorthyState(): Boolean = false

        override fun isAppVisible(): Boolean = true

        override fun currentTrack(): Track? = null

        override fun isPlaying(): Boolean = false

        override fun isPreparing(): Boolean = false

        override fun notifyMediaNotification(force: Boolean) {
            notificationForces += force
        }

        override fun refreshPlaybackSession() {
            refreshPlaybackSessionCalls++
        }
    }
}
