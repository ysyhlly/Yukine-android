package app.yukine.playback.manager

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.media.session.MediaSession
import app.yukine.R
import app.yukine.model.Track
import app.yukine.playback.service.PlaybackServiceActions
import androidx.media3.common.MediaMetadata
import java.util.function.Supplier

internal class PlaybackNotificationManager(
    private val context: Context,
    private val foregroundController: ForegroundController,
    private val stateProvider: StateProvider,
    private val lyricsPublisherSupplier: Supplier<out LyricsPublisher?>?,
    private val artworkProvider: ArtworkProvider,
    private val actionCallbacks: ActionCallbacks? = null
) : NotificationManager {
    interface ForegroundController {
        fun activityPendingIntent(): android.app.PendingIntent
        fun serviceActionPendingIntent(action: String, requestCode: Int): android.app.PendingIntent
        fun startPlaybackForeground(notification: Notification)
    }

    interface StateProvider {
        fun isQueueEmpty(): Boolean
        fun isPlaying(): Boolean
        fun isPreparing(): Boolean
        fun currentTrack(): Track?
        fun isFavorite(track: Track?): Boolean
        fun playbackSessionPlatformToken(): MediaSession.Token?
    }

    interface ArtworkProvider {
        fun notificationArtworkFor(track: Track?): Bitmap?
        fun notificationArtworkDataFor(track: Track?): ByteArray?
    }

    interface ActionCallbacks {
        fun publishPlaybackNotification(force: Boolean)
        fun play()
        fun pause()
        fun skipToPrevious()
        fun skipToNext()
        fun toggleCurrentFavorite()
        fun restoreLastPlayback(playWhenReady: Boolean)
        fun stopAndClear()
        fun stopForegroundAndSelf()
    }

    fun interface SessionRefresher {
        fun refreshPlaybackSession()
    }

    fun updateMediaNotification(force: Boolean) {
        val track = stateProvider.currentTrack()
        if (track == null && !hasNotificationWorthyState()) {
            return
        }
        val notification = playbackNotification(track)
        foregroundController.startPlaybackForeground(notification)
    }

    fun hasNotificationWorthyState(): Boolean {
        return isNotificationWorthy(
            currentTrack = stateProvider.currentTrack(),
            queueEmpty = stateProvider.isQueueEmpty(),
            preparing = stateProvider.isPreparing(),
            playing = stateProvider.isPlaying()
        )
    }

    fun handleServiceAction(action: String?): Boolean {
        if (!PlaybackServiceActions.isPlaybackServiceAction(action)) {
            return false
        }
        val callbacks = actionCallbacks ?: return false
        callbacks.publishPlaybackNotification(true)
        when (action) {
            PlaybackServiceActions.PLAY -> callbacks.play()
            PlaybackServiceActions.PAUSE -> callbacks.pause()
            PlaybackServiceActions.PREVIOUS -> callbacks.skipToPrevious()
            PlaybackServiceActions.NEXT -> callbacks.skipToNext()
            PlaybackServiceActions.TOGGLE_FAVORITE -> callbacks.toggleCurrentFavorite()
            PlaybackServiceActions.RESTORE -> callbacks.restoreLastPlayback(false)
            PlaybackServiceActions.RESTORE_AND_PLAY -> callbacks.restoreLastPlayback(true)
            PlaybackServiceActions.STOP -> callbacks.stopAndClear()
        }
        if (hasNotificationWorthyState()) {
            callbacks.publishPlaybackNotification(true)
        } else if (action != PlaybackServiceActions.STOP) {
            callbacks.stopForegroundAndSelf()
        }
        return true
    }

    fun lyricsNotificationBridge(sessionRefresher: SessionRefresher): PlaybackLyricsManager.NotificationBridge {
        return object : PlaybackLyricsManager.NotificationBridge {
            override fun hasNotificationWorthyState(): Boolean {
                return this@PlaybackNotificationManager.hasNotificationWorthyState()
            }

            override fun notifyMediaNotification(force: Boolean) {
                val callbacks = actionCallbacks
                if (callbacks != null) {
                    callbacks.publishPlaybackNotification(force)
                } else {
                    updateMediaNotification(force)
                }
            }

            override fun refreshPlaybackSession() {
                sessionRefresher.refreshPlaybackSession()
            }
        }
    }

    fun playbackNotification(track: Track?): Notification {
        val playing = stateProvider.isPlaying() || stateProvider.isPreparing()
        val hasTrack = track != null
        val isFavorite = stateProvider.isFavorite(track)
        val lyricText = currentNotificationLyric(track)
        val contentText = if (lyricText.isNotEmpty()) lyricText else if (hasTrack) track!!.subtitle() else EMPTY_NOTIFICATION_TEXT
        val titleText = if (hasTrack) track!!.title else EMPTY_NOTIFICATION_TITLE
        val capsuleText = if (lyricText.isNotEmpty()) shortCriticalText(lyricText) else if (hasTrack) shortCriticalText(track!!.title) else "Yukine"
        val favoriteTitle = if (isFavorite) "Favorited" else "Favorite"
        val favoriteIcon = if (isFavorite) R.drawable.ic_notif_favorite_filled else R.drawable.ic_notif_favorite_outline
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.drawable.ic_stat_echo)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(foregroundController.activityPendingIntent())
            .setTicker(contentText)
            .setSubText(if (hasTrack) track!!.subtitle() else "Yukine")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setColor(NOTIFICATION_ACCENT)
            .setShowWhen(false)
            .setOngoing(hasTrack || playing)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_notif_previous, "Previous", foregroundController.serviceActionPendingIntent(PlaybackServiceActions.PREVIOUS, 1))
            .addAction(if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play, if (playing) "Pause" else "Play", foregroundController.serviceActionPendingIntent(if (playing) PlaybackServiceActions.PAUSE else PlaybackServiceActions.RESTORE_AND_PLAY, 2))
            .addAction(R.drawable.ic_notif_next, "Next", foregroundController.serviceActionPendingIntent(PlaybackServiceActions.NEXT, 3))
            .addAction(favoriteIcon, favoriteTitle, foregroundController.serviceActionPendingIntent(PlaybackServiceActions.TOGGLE_FAVORITE, 4))
            .addAction(R.drawable.ic_notif_stop, "Stop", foregroundController.serviceActionPendingIntent(PlaybackServiceActions.STOP, 5))
        if (hasTrack) {
            val extras = Bundle()
            extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            extras.putString("extra_title", titleText)
            extras.putString("extra_body", contentText)
            extras.putString("extra_short_text", capsuleText)
            builder.addExtras(extras)
        }
        val artwork = if (hasTrack) artworkProvider.notificationArtworkFor(track) else null
        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
            val token = stateProvider.playbackSessionPlatformToken()
            if (token != null) {
                style.setMediaSession(token)
            }
            builder.setStyle(style)
        } else if (lyricText.isNotEmpty()) {
            builder.setStyle(Notification.BigTextStyle().bigText(lyricText))
        }
        return builder.build()
    }

    fun mediaMetadataForTrack(track: Track): MediaMetadata {
        val lyricText = currentNotificationLyric(track)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setDurationMs(if (track.durationMs > 0L) track.durationMs else null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (lyricText.isNotEmpty()) {
            val extras = Bundle()
            extras.putString(EXTRA_CURRENT_LYRIC, lyricText)
            extras.putString(EXTRA_LYRIC_TRACK_TITLE, track.title)
            metadata.setSubtitle(lyricText)
                .setDescription(lyricText)
                .setExtras(extras)
        }
        if (track.albumArtUri != null) {
            metadata.setArtworkUri(track.albumArtUri)
        }
        val artworkData = artworkProvider.notificationArtworkDataFor(track)
        if (artworkData != null && artworkData.isNotEmpty()) {
            metadata.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return metadata.build()
    }

    fun shortCriticalText(value: String): String {
        val compact = sanitizeNotificationLyric(value).replace('\n', ' ')
        if (compact.isEmpty()) return "Yukine"
        if (compact.length <= 9) return "\u266A $compact"
        if (compact.length <= 14) return "\u266A ${compact.substring(0, 9)}"
        return "\u266A ${compact.substring(0, 8)}..."
    }

    private fun currentNotificationLyric(track: Track?): String {
        return lyricsPublisherSupplier?.get()?.notificationLyricText(track).orEmpty()
    }

    private fun sanitizeNotificationLyric(value: String?): String {
        return lyricsPublisherSupplier?.get()?.sanitizeNotificationLyric(value).orEmpty()
    }

    companion object {
        @JvmStatic
        fun isNotificationWorthy(
            currentTrack: Track?,
            queueEmpty: Boolean,
            preparing: Boolean,
            playing: Boolean
        ): Boolean {
            return currentTrack != null || !queueEmpty || preparing || playing
        }

        private const val CHANNEL_ID = "echo_next_playback"
        private const val EMPTY_NOTIFICATION_TITLE = "Yukine"
        private const val EMPTY_NOTIFICATION_TEXT = ""
        private const val NOTIFICATION_ACCENT = 0xFFECECEC.toInt()
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val EXTRA_CURRENT_LYRIC = "extra_current_lyric"
        private const val EXTRA_LYRIC_TRACK_TITLE = "extra_lyric_track_title"
    }
}
