package app.yukine.playback

import android.graphics.Bitmap
import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.LyricsPublisher
import app.yukine.playback.state.PlaybackStateListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
    fun widgetSkipsProgressOnlySnapshotsButRefreshesForPlaybackOrArtworkChanges() {
        var snapshot = stateSnapshot(positionMs = 1_000L, playing = true)
        var artwork: Bitmap? = null
        var widgetUpdates = 0
        val publisher = PlaybackStatePublisher(
            snapshotProvider = { snapshot },
            lyricsPublisher = null,
            notificationUpdater = null,
            artworkProvider = PlaybackStatePublisher.ArtworkProvider { artwork },
            widgetUpdater = PlaybackStatePublisher.WidgetUpdater { _, _ -> widgetUpdates++ }
        )

        publisher.publishState()
        assertEquals(1, widgetUpdates)
        snapshot = stateSnapshot(positionMs = 2_000L, playing = true)
        publisher.publishState()
        assertEquals(1, widgetUpdates)
        snapshot = stateSnapshot(positionMs = 2_000L, playing = false)
        publisher.publishState()
        assertEquals(2, widgetUpdates)
        artwork = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        publisher.publishState()

        assertEquals(3, widgetUpdates)
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

    private fun stateSnapshot(positionMs: Long, playing: Boolean): PlaybackStateSnapshot {
        val track = Track(
            1L,
            "Title",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://example.com/audio.mp3"),
            "audio",
            0L,
            Uri.parse("https://example.com/artwork.jpg")
        )
        return PlaybackStateSnapshot(
            track,
            0,
            1,
            positionMs,
            track.durationMs,
            playing,
            false,
            "",
            false,
            0,
            1f,
            1f,
            0L
        )
    }
}
