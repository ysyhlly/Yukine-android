package app.yukine.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.LyricsPublisher
import app.yukine.playback.manager.PlaybackNotificationManager
import app.yukine.playback.service.PlaybackServiceActions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackNotificationManagerTest {
    @Test
    fun notificationWorthyStateIsOwnedByNotificationManager() {
        val state = FakeStateProvider()
        val foreground = FakeForegroundController()
        val manager = manager(state, foreground)

        assertFalse(manager.hasNotificationWorthyState())
        manager.updateMediaNotification(force = false)
        assertEquals(0, foreground.startedNotifications)

        state.queueEmpty = false

        assertTrue(manager.hasNotificationWorthyState())
        manager.updateMediaNotification(force = false)
        assertEquals(1, foreground.startedNotifications)
    }

    @Test
    fun nonForcedDuplicateUpdatesDoNotRepostForegroundNotification() {
        val state = FakeStateProvider()
        state.track = track(7L)
        state.playing = true
        val foreground = FakeForegroundController()
        val manager = manager(state, foreground)

        manager.updateMediaNotification(force = false)
        manager.updateMediaNotification(force = false)
        manager.updateMediaNotification(force = false)

        assertEquals(1, foreground.startedNotifications)

        manager.updateMediaNotification(force = true)

        assertEquals(2, foreground.startedNotifications)
    }

    @Test
    fun changedNotificationContentRepostsWithoutForce() {
        val state = FakeStateProvider()
        state.track = track(7L)
        state.playing = true
        val foreground = FakeForegroundController()
        val publisherSource = MutableLyricsPublisherSource()
        publisherSource.publisher = FakeLyricsPublisher("first")
        val manager = manager(
            state,
            foreground,
            lyricsPublisherSupplier = java.util.function.Supplier { publisherSource.publisher }
        )

        manager.updateMediaNotification(force = false)
        manager.updateMediaNotification(force = false)
        publisherSource.publisher = FakeLyricsPublisher("second")
        manager.updateMediaNotification(force = false)

        assertEquals(2, foreground.startedNotifications)
    }

    @Test
    fun notificationWorthyFallbackPolicyIsOwnedByNotificationManager() {
        assertFalse(
            PlaybackNotificationManager.isNotificationWorthy(
                currentTrack = null,
                queueEmpty = true,
                preparing = false,
                playing = false
            )
        )
        assertTrue(
            PlaybackNotificationManager.isNotificationWorthy(
                currentTrack = track(1L),
                queueEmpty = true,
                preparing = false,
                playing = false
            )
        )
        assertTrue(
            PlaybackNotificationManager.isNotificationWorthy(
                currentTrack = null,
                queueEmpty = false,
                preparing = false,
                playing = false
            )
        )
        assertTrue(
            PlaybackNotificationManager.isNotificationWorthy(
                currentTrack = null,
                queueEmpty = true,
                preparing = true,
                playing = false
            )
        )
        assertTrue(
            PlaybackNotificationManager.isNotificationWorthy(
                currentTrack = null,
                queueEmpty = true,
                preparing = false,
                playing = true
            )
        )
    }

    @Test
    fun notificationActionsMapToPlaybackServiceControls() {
        val state = FakeStateProvider()
        state.track = track(8L)
        state.playing = false
        val foreground = FakeForegroundController()
        val manager = manager(state, foreground)

        var notification = manager.playbackNotification(state.track)

        assertEquals(PlaybackServiceActions.PREVIOUS, foreground.actionFor(notification.actions[0]))
        assertEquals(PlaybackServiceActions.RESTORE_AND_PLAY, foreground.actionFor(notification.actions[1]))
        assertEquals(PlaybackServiceActions.NEXT, foreground.actionFor(notification.actions[2]))
        assertEquals(PlaybackServiceActions.TOGGLE_FAVORITE, foreground.actionFor(notification.actions[3]))
        assertEquals(PlaybackServiceActions.STOP, foreground.actionFor(notification.actions[4]))

        state.playing = true
        notification = manager.playbackNotification(state.track)

        assertEquals(PlaybackServiceActions.PAUSE, foreground.actionFor(notification.actions[1]))
        assertEquals(PlaybackServiceActions.STOP, foreground.actionFor(notification.actions[4]))
    }

    @Test
    fun favoriteActionReflectsNotificationFavoriteState() {
        val state = FakeStateProvider()
        state.track = track(7L)
        val manager = manager(state, FakeForegroundController())

        var notification = manager.playbackNotification(state.track)
        assertEquals("Favorite", notification.actions[3].title.toString())

        state.favoriteIds += 7L

        notification = manager.playbackNotification(state.track)
        assertEquals("Favorited", notification.actions[3].title.toString())
    }

    @Test
    fun playActionPublishesBeforeAndAfterCommand() {
        val state = FakeStateProvider()
        state.queueEmpty = false
        val actions = FakeActionCallbacks()
        val manager = manager(state, FakeForegroundController(), actions)

        assertTrue(manager.handleServiceAction(PlaybackServiceActions.PLAY))

        assertEquals(listOf("notify:true", "play", "notify:true"), actions.events)
    }

    @Test
    fun restoreAndPlayDelegatesWithPlayWhenReady() {
        val state = FakeStateProvider()
        state.queueEmpty = false
        val actions = FakeActionCallbacks()
        val manager = manager(state, FakeForegroundController(), actions)

        assertTrue(manager.handleServiceAction(PlaybackServiceActions.RESTORE_AND_PLAY))

        assertEquals(listOf("notify:true", "restore:true", "notify:true"), actions.events)
    }

    @Test
    fun serviceActionsRouteToCommandCallbacks() {
        val cases = listOf(
            PlaybackServiceActions.PLAY to "play",
            PlaybackServiceActions.PAUSE to "pause",
            PlaybackServiceActions.PREVIOUS to "previous",
            PlaybackServiceActions.NEXT to "next",
            PlaybackServiceActions.TOGGLE_FAVORITE to "favorite",
            PlaybackServiceActions.RESTORE to "restore:false",
            PlaybackServiceActions.RESTORE_AND_PLAY to "restore:true"
        )

        for ((action, expectedEvent) in cases) {
            val state = FakeStateProvider()
            state.queueEmpty = false
            val actions = FakeActionCallbacks()
            val manager = manager(state, FakeForegroundController(), actions)

            assertTrue(manager.handleServiceAction(action))

            assertEquals(
                "Unexpected callback events for $action",
                listOf("notify:true", expectedEvent, "notify:true"),
                actions.events
            )
        }
    }

    @Test
    fun validActionWithoutCallbacksIsIgnored() {
        val manager = manager(FakeStateProvider(), FakeForegroundController())

        assertFalse(manager.handleServiceAction(PlaybackServiceActions.NEXT))
    }

    @Test
    fun lyricsBridgeUsesNotificationManagerStateAndPublishCallback() {
        val state = FakeStateProvider()
        state.queueEmpty = false
        val actions = FakeActionCallbacks()
        val sessionRefresher = FakeSessionRefresher()
        val manager = manager(state, FakeForegroundController(), actions)

        val bridge = manager.lyricsNotificationBridge(sessionRefresher)

        assertTrue(bridge.hasNotificationWorthyState())
        bridge.notifyMediaNotification(false)
        bridge.refreshPlaybackSession()

        assertEquals(listOf("notify:false"), actions.events)
        assertEquals(1, sessionRefresher.refreshCalls)
    }

    @Test
    fun mediaMetadataForTrackIncludesTrackLyricsAndArtworkState() {
        val artworkData = byteArrayOf(1, 2, 3)
        val manager = manager(
            FakeStateProvider(),
            FakeForegroundController(),
            lyricsPublisherSupplier = java.util.function.Supplier { FakeLyricsPublisher("current lyric") },
            artworkProvider = FakeArtworkProvider(artworkData)
        )
        val track = track(9L)

        val metadata = manager.mediaMetadataForTrack(track)

        assertEquals("Track 9", metadata.title.toString())
        assertEquals("Artist", metadata.artist.toString())
        assertEquals("Album", metadata.albumTitle.toString())
        assertEquals(180_000L, metadata.durationMs)
        assertEquals(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC, metadata.mediaType)
        assertEquals("current lyric", metadata.subtitle.toString())
        assertEquals("current lyric", metadata.description.toString())
        assertEquals(track.albumArtUri, metadata.artworkUri)
        assertArrayEquals(artworkData, metadata.artworkData)
    }

    @Test
    fun notificationLyricsUseCurrentPublisherWhenAvailable() {
        val state = FakeStateProvider()
        state.track = track(9L)
        val publisherSource = MutableLyricsPublisherSource()
        val manager = manager(
            state,
            FakeForegroundController(),
            lyricsPublisherSupplier = java.util.function.Supplier { publisherSource.publisher }
        )

        assertEquals("Yukine", manager.shortCriticalText("line"))
        var metadata = manager.mediaMetadataForTrack(state.track!!)
        assertNull(metadata.subtitle)

        publisherSource.publisher = FakeLyricsPublisher("current lyric")

        metadata = manager.mediaMetadataForTrack(state.track!!)
        assertEquals("current lyric", metadata.subtitle.toString())
        assertEquals("\u266A current", manager.shortCriticalText(" current   "))
    }

    @Test
    fun nonStopActionWithoutNotificationWorthyStateStopsForegroundAndSelf() {
        val actions = FakeActionCallbacks()
        val manager = manager(FakeStateProvider(), FakeForegroundController(), actions)

        assertTrue(manager.handleServiceAction(PlaybackServiceActions.PAUSE))

        assertEquals(listOf("notify:true", "pause", "stopForegroundAndSelf"), actions.events)
    }

    @Test
    fun stopActionDoesNotRequestForegroundStopAfterClear() {
        val actions = FakeActionCallbacks()
        val manager = manager(FakeStateProvider(), FakeForegroundController(), actions)

        assertTrue(manager.handleServiceAction(PlaybackServiceActions.STOP))

        assertEquals(listOf("notify:true", "stopAndClear"), actions.events)
    }

    @Test
    fun unknownActionIsIgnored() {
        val actions = FakeActionCallbacks()
        val manager = manager(FakeStateProvider(), FakeForegroundController(), actions)

        assertFalse(manager.handleServiceAction("unknown"))

        assertTrue(actions.events.isEmpty())
    }

    private fun manager(
        stateProvider: FakeStateProvider,
        foregroundController: FakeForegroundController,
        actionCallbacks: PlaybackNotificationManager.ActionCallbacks? = null,
        lyricsPublisherSupplier: java.util.function.Supplier<out LyricsPublisher?>? = null,
        artworkProvider: PlaybackNotificationArtworkSource = FakeArtworkProvider()
    ): PlaybackNotificationManager {
        return PlaybackNotificationManager(
            RuntimeEnvironment.getApplication(),
            foregroundController,
            stateProvider,
            lyricsPublisherSupplier,
            artworkProvider,
            actionCallbacks
        )
    }

    private fun track(id: Long): Track {
        return Track(
            id,
            "Track $id",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("content://track/$id"),
            "/music/$id",
            0L,
            null
        )
    }

    private class FakeStateProvider : PlaybackNotificationManager.StateProvider {
        var queueEmpty = true
        var playing = false
        var preparing = false
        var track: Track? = null
        val favoriteIds = mutableSetOf<Long>()

        override fun isQueueEmpty(): Boolean = queueEmpty

        override fun isPlaying(): Boolean = playing

        override fun isPreparing(): Boolean = preparing

        override fun currentTrack(): Track? = track

        override fun isFavorite(track: Track?): Boolean {
            return track != null && favoriteIds.contains(track.id)
        }

        override fun playbackSessionPlatformToken(): MediaSession.Token? = null
    }

    private class FakeForegroundController : PlaybackNotificationManager.ForegroundController {
        var startedNotifications = 0
        private val pendingIntentActions = mutableMapOf<PendingIntent, String>()

        override fun activityPendingIntent(): PendingIntent {
            return pendingIntent()
        }

        override fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = pendingIntent(requestCode)
            pendingIntentActions[intent] = action
            return intent
        }

        override fun startPlaybackForeground(notification: Notification) {
            startedNotifications++
        }

        fun actionFor(action: Notification.Action): String? {
            assertNotNull(action.actionIntent)
            return pendingIntentActions[action.actionIntent]
        }

        private fun pendingIntent(requestCode: Int = 0): PendingIntent {
            val context: Context = RuntimeEnvironment.getApplication()
            return PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, PlaybackNotificationManagerTest::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private class MutableLyricsPublisherSource {
        var publisher: LyricsPublisher? = null
    }

    private class FakeLyricsPublisher(
        private val lyric: String
    ) : LyricsPublisher {
        override fun bind() {
        }

        override fun release() {
        }

        override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        }

        override fun onAppVisibilityChanged() {
        }

        override fun syncFloatingLyricsPlaybackState(snapshot: PlaybackStateSnapshot) {
        }

        override fun notificationLyricText(track: Track?): String = lyric

        override fun sanitizeNotificationLyric(value: String?): String = value.orEmpty().trim().replace("   ", " ")
    }

    private class FakeArtworkProvider(
        private val artworkData: ByteArray? = null
    ) : PlaybackNotificationArtworkSource {
        override fun notificationArtworkFor(track: Track?): Bitmap? = null

        override fun notificationArtworkDataFor(track: Track?): ByteArray? = artworkData
    }

    private class FakeSessionRefresher : PlaybackNotificationManager.SessionRefresher {
        var refreshCalls = 0

        override fun refreshPlaybackSession() {
            refreshCalls++
        }
    }

    private class FakeActionCallbacks : PlaybackNotificationManager.ActionCallbacks {
        val events = mutableListOf<String>()

        override fun publishPlaybackNotification(force: Boolean) {
            events += "notify:$force"
        }

        override fun play() {
            events += "play"
        }

        override fun pause() {
            events += "pause"
        }

        override fun skipToPrevious() {
            events += "previous"
        }

        override fun skipToNext() {
            events += "next"
        }

        override fun toggleCurrentFavorite() {
            events += "favorite"
        }

        override fun restoreLastPlayback(playWhenReady: Boolean) {
            events += "restore:$playWhenReady"
        }

        override fun stopAndClear() {
            events += "stopAndClear"
        }

        override fun stopForegroundAndSelf() {
            events += "stopForegroundAndSelf"
        }
    }
}
