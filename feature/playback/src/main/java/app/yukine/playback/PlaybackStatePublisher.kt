package app.yukine.playback

import android.graphics.Bitmap
import app.yukine.model.Track
import app.yukine.playback.manager.LyricsPublisher
import app.yukine.playback.state.PlaybackStateListener
import java.util.concurrent.CopyOnWriteArraySet

internal class PlaybackStatePublisher(
    private val snapshotProvider: () -> PlaybackStateSnapshot,
    private val lyricsPublisher: LyricsPublisher?,
    private val notificationUpdater: NotificationUpdater?,
    private val artworkProvider: ArtworkProvider?,
    private val widgetUpdater: WidgetUpdater
) {
    private val listeners = CopyOnWriteArraySet<PlaybackStateListener>()
    private var released = false
    private var lastWidgetSignature: WidgetSignature? = null

    fun publishState() {
        if (released) {
            return
        }
        val snapshot = snapshotProvider()
        lyricsPublisher?.syncFloatingLyricsPlaybackState(snapshot)
        notificationUpdater?.updateMediaNotification(false)
        val artwork = artworkProvider?.notificationArtworkFor(snapshot.currentTrack)
        updateWidgetIfChanged(snapshot, artwork)
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(snapshot)
        }
    }

    fun publishNotification(force: Boolean) {
        if (released) {
            return
        }
        notificationUpdater?.updateMediaNotification(force)
    }

    fun registerListener(listener: PlaybackStateListener?) {
        if (released || listener == null) {
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
        if (released) {
            return
        }
        val snapshot = snapshotProvider()
        recordBuffering?.record(snapshot)
        listeners.forEach { listener ->
            listener.onPlaybackBuffering(snapshot)
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        listeners.clear()
    }

    private fun updateWidgetIfChanged(snapshot: PlaybackStateSnapshot, artwork: Bitmap?) {
        val signature = WidgetSignature.from(snapshot, artwork)
        if (signature == lastWidgetSignature) {
            return
        }
        lastWidgetSignature = signature
        widgetUpdater.update(snapshot, artwork)
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

    private data class WidgetSignature(
        val trackId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUri: String,
        val playing: Boolean,
        val artwork: Bitmap?
    ) {
        companion object {
            fun from(snapshot: PlaybackStateSnapshot, artwork: Bitmap?): WidgetSignature {
                val track = snapshot.currentTrack
                return WidgetSignature(
                    trackId = track?.id ?: -1L,
                    title = track?.title.orEmpty(),
                    artist = track?.artist.orEmpty(),
                    album = track?.album.orEmpty(),
                    artworkUri = track?.albumArtUri?.toString().orEmpty(),
                    playing = snapshot.playing,
                    artwork = artwork
                )
            }
        }
    }
}
