package app.yukine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import app.yukine.data.EmbeddedArtwork
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveLyricsNotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "echo_live_lyrics_cloud"
        private const val LEGACY_CHANNEL_ID = "live_text_notification"
        private const val NOTIFICATION_ID = 2301
        private const val ACTION_STOP = "app.yukine.live_lyrics.STOP"
        private const val ACTION_REFRESH = "app.yukine.live_lyrics.REFRESH"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_BODY = "extra_body"
        private const val EXTRA_SHORT_TEXT = "extra_short_text"
        private const val EXTRA_SHOW_CLOSE_ACTION = "extra_show_close_action"
        private const val EXTRA_SHOW_NOTIFICATION_ICON = "extra_show_notification_icon"
        private const val EXTRA_CURRENT_LYRIC = "app.yukine.extra.CURRENT_LYRIC"
        private const val EXTRA_LYRIC_TRACK_TITLE = "app.yukine.extra.LYRIC_TRACK_TITLE"
        private const val EXTRA_LYRIC_TRACK_ARTIST = "app.yukine.extra.LYRIC_TRACK_ARTIST"
        private const val EXTRA_LYRIC_ALBUM_ART_URI = "app.yukine.extra.LYRIC_ALBUM_ART_URI"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val ARTWORK_TARGET_PX = 320

        @JvmStatic
        fun start(context: Context) {
            val state = notificationState(FloatingLyricsPublisher.snapshot())
            val intent = Intent(context, LiveLyricsNotificationService::class.java)
                .setAction(ACTION_REFRESH)
                .putExtra(EXTRA_TITLE, state.trackTitle)
                .putExtra(EXTRA_BODY, state.activeLine)
                .putExtra(EXTRA_SHORT_TEXT, shortCriticalText(state.activeLine))
                .putExtra(EXTRA_SHOW_CLOSE_ACTION, false)
                .putExtra(EXTRA_SHOW_NOTIFICATION_ICON, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, LiveLyricsNotificationService::class.java))
        }

        private fun notificationState(state: FloatingLyricsState): FloatingLyricsState =
            state.copy(
                trackTitle = sanitizeTitle(state.trackTitle),
                artist = sanitizeTitle(state.artist),
                activeLine = liveLyricText(state),
                visible = state.trackTitle.isNotBlank() || state.activeLine.isNotBlank()
            )

        private fun liveLyricText(state: FloatingLyricsState): String {
            val lyric = sanitizeLyric(state.activeLine)
            if (lyric.isNotBlank()) {
                return lyric
            }
            return if (state.trackTitle.isNotBlank()) {
                "\u672a\u627e\u5230\u6b4c\u8bcd"
            } else {
                "\u7b49\u5f85\u6b4c\u8bcd"
            }
        }

        private fun shortCriticalText(lyric: String): String {
            val compact = sanitizeLyric(lyric).replace('\n', ' ')
            if (compact.isBlank()) return "Yukine"
            val musicPrefix = "\u266A "
            return when {
                compact.length <= 9 -> musicPrefix + compact
                compact.length <= 14 -> musicPrefix + compact.substring(0, 9)
                else -> musicPrefix + compact.substring(0, 8) + "..."
            }
        }

        private fun sanitizeTitle(value: String?): String =
            value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim().let {
                if (it.length > 80) it.substring(0, 79) + "..." else it
            }

        private fun sanitizeLyric(value: String?): String {
            val lines = value.orEmpty()
                .replace('\r', '\n')
                .split('\n')
                .map { line ->
                    var text = line.trim()
                    while (text.contains("  ")) {
                        text = text.replace("  ", " ")
                    }
                    text
                }
                .filter { it.isNotBlank() }
                .take(2)
            val text = lines.joinToString("\n")
            return if (text.length > 140) {
                text.substring(0, 139) + "..."
            } else {
                text
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var lastState = FloatingLyricsState()
    private var artworkKey = ""
    private var artworkBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lastState = notificationState(FloatingLyricsPublisher.snapshot())
        if (!startForegroundSafely(lastState)) {
            stopSelf()
            return
        }
        observeLyrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateFromIntentOrSnapshot(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun observeLyrics() {
        collectJob = scope.launch {
            FloatingLyricsPublisher.state.collectLatest { state ->
                updateLyrics(state)
            }
        }
    }

    private fun updateFromIntentOrSnapshot(intent: Intent?) {
        val snapshot = FloatingLyricsPublisher.snapshot()
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        if (body.isBlank()) {
            updateLyrics(snapshot)
            return
        }
        updateLyrics(
            snapshot.copy(
                trackTitle = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { snapshot.trackTitle },
                activeLine = body
            )
        )
    }

    private fun updateLyrics(state: FloatingLyricsState) {
        val next = notificationState(state)
        if (next == lastState) {
            return
        }
        lastState = next
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(next))
        } catch (_: RuntimeException) {
            stopSelf()
        }
    }

    private fun startForegroundSafely(state: FloatingLyricsState): Boolean =
        try {
            startForeground(NOTIFICATION_ID, buildNotification(state))
            true
        } catch (_: RuntimeException) {
            false
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            runCatching { notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
            val channel = NotificationChannel(
                CHANNEL_ID,
                "\u5b9e\u65f6\u6b4c\u8bcd\u6d41\u4f53\u4e91",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "\u7528\u4e8e\u72b6\u6001\u680f\u3001\u9501\u5c4f\u548c OPPO \u6d41\u4f53\u4e91\u663e\u793a\u5b9e\u65f6\u6b4c\u8bcd"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: FloatingLyricsState): Notification {
        val lyricLine = state.activeLine.ifBlank { "\u7b49\u5f85\u6b4c\u8bcd" }
        val lyricLines = lyricDisplayLines(lyricLine)
        val appTitle = "Yukine"
        val primaryLyric = lyricLines.firstOrNull().orEmpty().ifBlank { "\u7b49\u5f85\u6b4c\u8bcd" }
        val displayText = lyricLines.joinToString("\n").ifBlank { primaryLyric }
        val trackInfo = trackInfo(state.trackTitle, state.artist)
        val capsuleText = shortCriticalText(lyricLine)
        val artwork = artworkFor(state.albumArtUri)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_echo)
            .setContentTitle(appTitle)
            .setContentText(displayText)
            .setTicker(displayText)
            .setSubText(trackInfo)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(activityPendingIntent())
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.BigTextStyle().bigText(displayText))
            .addExtras(Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                putString(EXTRA_TITLE, appTitle)
                putString(EXTRA_BODY, displayText)
                putString(EXTRA_SHORT_TEXT, capsuleText)
                putBoolean(EXTRA_SHOW_CLOSE_ACTION, false)
                putBoolean(EXTRA_SHOW_NOTIFICATION_ICON, true)
                putBoolean("oplus_smallicon_use_app_icon", true)
                putString(Notification.EXTRA_TITLE, appTitle)
                putString(Notification.EXTRA_TEXT, displayText)
                putString(Notification.EXTRA_BIG_TEXT, displayText)
                putString(Notification.EXTRA_SUB_TEXT, trackInfo)
                putString(Notification.EXTRA_SUMMARY_TEXT, trackInfo)
                putCharSequenceArray(Notification.EXTRA_TEXT_LINES, lyricLines.toTypedArray())
                putString(EXTRA_CURRENT_LYRIC, lyricLine)
                putString(EXTRA_LYRIC_TRACK_TITLE, state.trackTitle)
                putString(EXTRA_LYRIC_TRACK_ARTIST, state.artist)
                putString(EXTRA_LYRIC_ALBUM_ART_URI, state.albumArtUri.orEmpty())
            })
        artwork?.let { builder.setLargeIcon(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        requestPromotedOngoing(builder, true)
        setShortCriticalText(builder, capsuleText)
        return builder.build()
    }

    private fun lyricDisplayLines(lyricLine: String): List<String> =
        sanitizeLyric(lyricLine)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(2)

    private fun trackInfo(trackTitle: String, artist: String): String =
        when {
            trackTitle.isNotBlank() && artist.isNotBlank() -> "$trackTitle - $artist"
            trackTitle.isNotBlank() -> trackTitle
            artist.isNotBlank() -> artist
            else -> "Yukine"
        }

    private fun activityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            2,
            Intent(this, LiveLyricsNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestPromotedOngoing(builder: Notification.Builder, requested: Boolean) {
        try {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, requested)
        } catch (_: ReflectiveOperationException) {
            // API 36+ has a public method. Older/OEM builds still get the documented extra.
        } catch (_: RuntimeException) {
        }
    }

    private fun setShortCriticalText(builder: Notification.Builder, text: String) {
        if (text.isBlank()) {
            return
        }
        try {
            Notification.Builder::class.java
                .getMethod("setShortCriticalText", String::class.java)
                .invoke(builder, text)
        } catch (_: ReflectiveOperationException) {
        } catch (_: RuntimeException) {
        }
    }

    private fun artworkFor(albumArtUri: String?): Bitmap? {
        val key = albumArtUri.orEmpty()
        if (key.isBlank()) {
            artworkKey = ""
            artworkBitmap = null
            return null
        }
        if (key == artworkKey && artworkBitmap != null) {
            return artworkBitmap
        }
        val uri = Uri.parse(key)
        val scheme = uri.scheme.orEmpty()
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            artworkKey = key
            artworkBitmap = null
            fetchHttpArtworkAsync(key)
            return null
        }
        val bitmap = decodeArtwork(uri)
        artworkKey = key
        artworkBitmap = bitmap
        return bitmap
    }

    private fun fetchHttpArtworkAsync(url: String) {
        scope.launch(Dispatchers.IO) {
            val bitmap = try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.inputStream.use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val bytes = stream.readBytes()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
                    else {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (bitmap != null && artworkKey == url) {
                artworkBitmap = bitmap
                withContext(Dispatchers.Main) {
                    try {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(NOTIFICATION_ID, buildNotification(lastState))
                    } catch (_: RuntimeException) { }
                }
            }
        }
    }

    private fun decodeArtwork(uri: Uri): Bitmap? {
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            val bytes = EmbeddedArtwork.read(this, uri) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openArtworkStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return openArtworkStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun openArtworkStream(uri: Uri): InputStream? {
        val scheme = uri.scheme.orEmpty()
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            return null
        }
        return try {
            contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > ARTWORK_TARGET_PX || height / sample > ARTWORK_TARGET_PX) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
