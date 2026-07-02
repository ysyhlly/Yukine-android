package app.yukine.playback

import app.yukine.model.Track
import app.yukine.playback.manager.LyricsPublisher
import app.yukine.playback.state.PlaybackStateListener
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatePublisherTest {
    @Test
    fun publishStateFansOutToLyricsNotificationAndWidget() {
        val snapshot = PlaybackStateSnapshot.empty()
        val calls = mutableListOf<String>()
        val publisher = PlaybackStatePublisher(
            snapshotProvider = { snapshot },
            lyricsPublisher = object : LyricsPublisher {
                override fun bind() {}
                override fun release() {}
                override fun setStatusBarLyricsEnabled(enabled: Boolean) {}
                override fun syncFloatingLyricsPlaybackState(snapshot: PlaybackStateSnapshot) {
                    calls.add("lyrics")
                }
                override fun notificationLyricText(track: Track?): String = ""
                override fun sanitizeNotificationLyric(value: String?): String = value ?: ""
            },
            notificationUpdater = PlaybackStatePublisher.NotificationUpdater {
                calls.add(if (it) "notify-true" else "notify-false")
            },
            artworkProvider = PlaybackStatePublisher.ArtworkProvider {
                calls.add("artwork")
                null
            },
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ ->
                calls.add("widget")
            }
        )

        publisher.publishState()

        assertEquals(listOf("lyrics", "notify-false", "artwork", "widget"), calls)
    }

    @Test
    fun registerListenerDeliversCurrentSnapshotAndReceivesFutureUpdates() {
        val snapshot = PlaybackStateSnapshot.empty()
        val calls = mutableListOf<String>()
        val publisher = PlaybackStatePublisher(
            snapshotProvider = { snapshot },
            lyricsPublisher = null,
            notificationUpdater = null,
            artworkProvider = null,
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ -> }
        )
        val listener = object : PlaybackStateListener {
            override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
                calls.add("state")
            }
        }

        publisher.registerListener(listener)
        publisher.publishState()
        publisher.unregisterListener(listener)
        publisher.publishState()

        assertEquals(listOf("state", "state"), calls)
    }

    @Test
    fun publishBufferingStateFansOutThroughListenerAndRecorder() {
        val snapshot = PlaybackStateSnapshot.empty()
        val calls = mutableListOf<String>()
        val publisher = PlaybackStatePublisher(
            snapshotProvider = { snapshot },
            lyricsPublisher = null,
            notificationUpdater = null,
            artworkProvider = null,
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ -> }
        )
        val listener = object : PlaybackStateListener {
            override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) = Unit

            override fun onPlaybackBuffering(snapshot: PlaybackStateSnapshot) {
                calls.add("buffering")
            }
        }
        publisher.registerListener(listener)

        publisher.publishBufferingState { calls.add("record") }

        assertEquals(listOf("record", "buffering"), calls)
    }

    @Test
    fun releaseClearsListenersAndStopsFutureCallbacks() {
        val snapshot = PlaybackStateSnapshot.empty()
        val calls = mutableListOf<String>()
        val publisher = PlaybackStatePublisher(
            snapshotProvider = { snapshot },
            lyricsPublisher = null,
            notificationUpdater = PlaybackStatePublisher.NotificationUpdater {
                calls.add("notify")
            },
            artworkProvider = null,
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ ->
                calls.add("widget")
            }
        )
        val listener = object : PlaybackStateListener {
            override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
                calls.add("state")
            }

            override fun onPlaybackBuffering(snapshot: PlaybackStateSnapshot) {
                calls.add("buffering")
            }
        }

        publisher.registerListener(listener)
        calls.clear()
        publisher.release()
        publisher.publishState()
        publisher.publishNotification(true)
        publisher.publishBufferingState { calls.add("record") }
        publisher.registerListener(listener)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun releaseIsIdempotentAfterListenersAreCleared() {
        val snapshot = PlaybackStateSnapshot.empty()
        var snapshotReads = 0
        val calls = mutableListOf<String>()
        val publisher = PlaybackStatePublisher(
            snapshotProvider = {
                snapshotReads++
                snapshot
            },
            lyricsPublisher = null,
            notificationUpdater = PlaybackStatePublisher.NotificationUpdater {
                calls.add("notify")
            },
            artworkProvider = null,
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ ->
                calls.add("widget")
            }
        )
        val listener = object : PlaybackStateListener {
            override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
                calls.add("state")
            }

            override fun onPlaybackBuffering(snapshot: PlaybackStateSnapshot) {
                calls.add("buffering")
            }
        }

        publisher.registerListener(listener)
        calls.clear()
        val readsAfterRegistration = snapshotReads
        publisher.release()
        publisher.release()
        publisher.publishState()
        publisher.publishNotification(true)
        publisher.publishBufferingState { calls.add("record") }
        publisher.registerListener(listener)

        assertEquals(readsAfterRegistration, snapshotReads)
        assertEquals(emptyList<String>(), calls)
    }
}
