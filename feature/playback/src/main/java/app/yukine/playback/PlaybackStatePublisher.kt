package app.yukine.playback

import android.graphics.Bitmap
import app.yukine.model.Track
import app.yukine.playback.manager.LyricsPublisher
import java.util.concurrent.CopyOnWriteArraySet

internal class PlaybackStatePublisher(
    private val snapshotProvider: () -> PlaybackStateSnapshot,
    private val lyricsPublisher: LyricsPublisher?,
    private val notificationUpdater: NotificationUpdater?,
    private val artworkProvider: ArtworkProvider?,
    private val widgetUpdater: WidgetUpdater
) {
    private val listeners = CopyOnWriteArraySet<PlaybackStateListener>()

    fun publishState() {
        val snapshot = snapshotProvider()
        lyricsPublisher?.syncFloatingLyricsPlaybackState(snapshot)
        notificationUpdater?.updateMediaNotification(false)
        val artwork = artworkProvider?.notificationArtworkFor(snapshot.currentTrack)
        widgetUpdater.update(snapshot, artwork)
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(snapshot)
        }
    }

    fun publishNotification(force: Boolean) {
        notificationUpdater?.updateMediaNotification(force)
    }

    fun registerListener(listener: PlaybackStateListener?) {
        if (listener == null) {
            return
        }
        listeners.add(listener)
        listener.onPlaybackStateChanged(snapshotProvider())
    }

    fun unregisterListener(listener: PlaybackStateListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun publishBufferingState(recordBuffering: BufferingRecorder?) {
        val snapshot = snapshotProvider()
        recordBuffering?.record(snapshot)
        listeners.forEach { listener ->
            listener.onPlaybackBuffering(snapshot)
        }
    }

    internal fun interface BufferingRecorder {
        fun record(snapshot: PlaybackStateSnapshot)
    }

    internal fun interface NotificationUpdater {
        fun updateMediaNotification(force: Boolean)
    }

    internal fun interface WidgetUpdater {
        fun update(snapshot: PlaybackStateSnapshot, artwork: Bitmap?)
    }

    internal fun interface ArtworkProvider {
        fun notificationArtworkFor(track: Track?): Bitmap?
    }
}
