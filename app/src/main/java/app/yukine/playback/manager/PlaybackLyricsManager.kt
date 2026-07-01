package app.yukine.playback.manager

import android.content.Context
import app.yukine.FloatingLyricsPublisher
import app.yukine.FloatingLyricsState
import app.yukine.LiveLyricsNotificationService
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal class PlaybackLyricsManager(
    private val context: Context,
    private val stateProvider: StateProvider,
    private val notificationBridge: NotificationBridge
) : LyricsPublisher {
    interface StateProvider {
        fun isAppVisible(): Boolean
        fun currentTrack(): Track?
        fun isPlaying(): Boolean
        fun isPreparing(): Boolean
    }

    interface NotificationBridge {
        fun hasNotificationWorthyState(): Boolean
        fun notifyMediaNotification(force: Boolean)
        fun refreshPlaybackSession()
    }

    private var statusBarLyricsEnabled = true
    private var lastNotificationLyric = ""
    private var lastLyricNotificationUpdateAtMs = 0L
    private var released = false

    private val floatingLyricsListener = FloatingLyricsPublisher.Listener { state ->
        if (released) {
            return@Listener
        }
        val nextLyric = state?.let { sanitizeNotificationLyric(it.activeLine) } ?: ""
        if (nextLyric == lastNotificationLyric) {
            return@Listener
        }
        lastNotificationLyric = nextLyric
        if (notificationBridge.hasNotificationWorthyState()) {
            val now = System.currentTimeMillis()
            if (!stateProvider.isAppVisible() && now - lastLyricNotificationUpdateAtMs < BACKGROUND_LYRIC_NOTIFICATION_MIN_INTERVAL_MS) {
                return@Listener
            }
            lastLyricNotificationUpdateAtMs = now
            notificationBridge.notifyMediaNotification(false)
            updateLiveLyricsNotificationService()
        }
    }

    override fun bind() {
        if (released) {
            return
        }
        FloatingLyricsPublisher.addListener(floatingLyricsListener)
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        FloatingLyricsPublisher.removeListener(floatingLyricsListener)
        LiveLyricsNotificationService.stop(context)
    }

    override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        if (released) {
            return
        }
        if (statusBarLyricsEnabled == enabled) {
            return
        }
        statusBarLyricsEnabled = enabled
        lastNotificationLyric = ""
        updateLiveLyricsNotificationService()
        notificationBridge.refreshPlaybackSession()
        notificationBridge.notifyMediaNotification(true)
    }

    override fun syncFloatingLyricsPlaybackState(snapshot: PlaybackStateSnapshot) {
        if (released) {
            return
        }
        if (snapshot.currentTrack == null) {
            return
        }
        val state = FloatingLyricsPublisher.snapshot()
        val track = snapshot.currentTrack
        val activeLine = if (floatingLyricsTrackMatches(state, track)) {
            state.activeLine
        } else {
            ""
        }
        FloatingLyricsPublisher.update(
            track.title,
            track.artist,
            track.albumArtUriString(),
            snapshot.playing || snapshot.preparing,
            activeLine
        )
    }

    override fun notificationLyricText(track: Track?): String {
        if (released) {
            return ""
        }
        if (!statusBarLyricsEnabled || track == null) {
            return ""
        }
        val state = FloatingLyricsPublisher.snapshot()
        if (track.title != state.trackTitle) {
            return ""
        }
        val activeLine = state.activeLine
        return if (activeLine.isBlank()) "" else sanitizeNotificationLyric(activeLine)
    }

    override fun sanitizeNotificationLyric(value: String?): String {
        if (value == null) {
            return ""
        }
        val rawLines = value.replace('\r', '\n').split("\n")
        val builder = StringBuilder()
        var count = 0
        for (rawLine in rawLines) {
            var line = rawLine.trim()
            while (line.contains("  ")) {
                line = line.replace("  ", " ")
            }
            if (line.isEmpty()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(line)
            count++
            if (count >= 2) {
                break
            }
        }
        val text = builder.toString()
        return if (text.length > 140) text.substring(0, 139) + "..." else text
    }

    private fun floatingLyricsTrackMatches(state: FloatingLyricsState, track: Track): Boolean {
        return track.title == state.trackTitle && track.artist == state.artist
    }

    private fun updateLiveLyricsNotificationService() {
        if (!statusBarLyricsEnabled || stateProvider.currentTrack() == null || (!stateProvider.isPlaying() && !stateProvider.isPreparing())) {
            LiveLyricsNotificationService.stop(context)
            return
        }
        try {
            LiveLyricsNotificationService.start(context)
        } catch (_: RuntimeException) {
        }
    }

    companion object {
        private const val BACKGROUND_LYRIC_NOTIFICATION_MIN_INTERVAL_MS = 1500L
    }
}
