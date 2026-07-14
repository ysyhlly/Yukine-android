package app.yukine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.ui.LyricUiLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class FloatingLyricsState(
    val trackTitle: String = "",
    val artist: String = "",
    val albumArtUri: String? = null,
    val playing: Boolean = false,
    val activeLine: String = "",
    val visible: Boolean = false,
    val trackId: Long = -1L
)

/**
 * Static publisher for lyrics state that the floating service observes.
 * Updated by NowPlayingViewModel whenever lyrics change.
 */
object FloatingLyricsPublisher {
    fun interface Listener {
        fun onFloatingLyricsChanged(state: FloatingLyricsState)
    }

    private val _state = MutableStateFlow(FloatingLyricsState())
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<Listener>()
    val state: StateFlow<FloatingLyricsState> = _state.asStateFlow()
    @Volatile private var lyricsTimeline = LyricsTimeline()

    private data class LyricsTimeline(
        val trackId: Long = -1L,
        val lines: List<LyricsLine> = emptyList(),
        val offsetMs: Long = 0L
    )

    @JvmStatic
    fun update(state: NowPlayingUiState, lyricsState: LyricsState?) {
        val matchingTimeline = lyricsState?.takeIf { it.trackId == state.track.trackId }
        val activeLine = state.lyrics.lines.firstOrNull { it.active }?.text
            ?: state.lyrics.lines.firstOrNull()?.text
            ?: ""
        update(
            state.track.trackId,
            state.track.title,
            state.track.artist,
            state.artwork.coverUri,
            state.progress.playing,
            activeLine,
            matchingTimeline?.lines.orEmpty(),
            matchingTimeline?.offsetMs ?: 0L
        )
    }

    @JvmStatic
    fun update(trackTitle: String, lyrics: List<LyricUiLine>) {
        val previous = _state.value
        val active = lyrics.firstOrNull { it.active }?.text
            ?: lyrics.firstOrNull()?.text
            ?: ""
        val sameTrack = previous.trackTitle == trackTitle
        if (!sameTrack) {
            clearLyricsTimeline()
        }
        publish(
            FloatingLyricsState(
                trackTitle = trackTitle,
                artist = previous.artist,
                albumArtUri = previous.albumArtUri,
                playing = previous.playing,
                activeLine = active,
                visible = active.isNotBlank(),
                trackId = if (sameTrack) previous.trackId else -1L
            )
        )
    }

    @JvmStatic
    fun update(
        trackId: Long,
        trackTitle: String,
        artist: String,
        albumArtUri: String?,
        playing: Boolean,
        activeLine: String,
        lyrics: List<LyricsLine>,
        lyricsOffsetMs: Long
    ) {
        updateLyricsTimeline(trackId, lyrics, lyricsOffsetMs)
        publish(
            FloatingLyricsState(
                trackTitle = trackTitle,
                artist = artist,
                albumArtUri = albumArtUri,
                playing = playing,
                activeLine = activeLine,
                visible = activeLine.isNotBlank(),
                trackId = trackId
            )
        )
    }

    /**
     * Uses the timeline published by the Activity to keep lyrics moving from the playback
     * service's progress updates after the Activity has been destroyed.
     */
    @JvmStatic
    fun syncPlaybackState(track: Track, playing: Boolean, positionMs: Long) {
        val timeline = lyricsTimeline
        val activeLine = if (timeline.trackId == track.id) {
            activeLineAt(timeline.lines, (positionMs + timeline.offsetMs).coerceAtLeast(0L))
        } else {
            ""
        }
        publish(
            FloatingLyricsState(
                trackTitle = track.title,
                artist = track.artist,
                albumArtUri = track.albumArtUri?.toString(),
                playing = playing,
                activeLine = activeLine,
                visible = activeLine.isNotBlank(),
                trackId = track.id
            )
        )
    }

    @JvmStatic
    fun update(
        trackTitle: String,
        artist: String,
        albumArtUri: String?,
        playing: Boolean,
        activeLine: String
    ) {
        val previous = _state.value
        val sameTrack = previous.trackTitle == trackTitle && previous.artist == artist
        if (!sameTrack) {
            clearLyricsTimeline()
        }
        publish(
            FloatingLyricsState(
                trackTitle = trackTitle,
                artist = artist,
                albumArtUri = albumArtUri,
                playing = playing,
                activeLine = activeLine,
                visible = activeLine.isNotBlank(),
                trackId = if (sameTrack) previous.trackId else -1L
            )
        )
    }

    private fun updateLyricsTimeline(trackId: Long, lines: List<LyricsLine>, offsetMs: Long) {
        val previous = lyricsTimeline
        if (previous.trackId == trackId && previous.offsetMs == offsetMs && previous.lines == lines) {
            return
        }
        lyricsTimeline = LyricsTimeline(trackId, lines.toList(), offsetMs)
    }

    private fun clearLyricsTimeline() {
        lyricsTimeline = LyricsTimeline()
    }

    private fun activeLineAt(lines: List<LyricsLine>, positionMs: Long): String {
        var active: LyricsLine? = null
        for (line in lines) {
            if (line.timeMs <= positionMs) {
                active = line
            } else {
                break
            }
        }
        return active?.text ?: lines.firstOrNull()?.text.orEmpty()
    }

    @JvmStatic
    fun clear() {
        clearLyricsTimeline()
        publish(FloatingLyricsState())
    }

    @JvmStatic
    fun snapshot(): FloatingLyricsState = _state.value

    @JvmStatic
    fun addListener(listener: Listener?) {
        if (listener != null) {
            listeners.add(listener)
            listener.onFloatingLyricsChanged(_state.value)
        }
    }

    @JvmStatic
    fun removeListener(listener: Listener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    private fun publish(next: FloatingLyricsState) {
        if (_state.value == next) {
            return
        }
        _state.value = next
        listeners.forEach { it.onFloatingLyricsChanged(next) }
    }
}

class FloatingLyricsService : Service() {

    companion object {
        private const val CHANNEL_ID = "echo_floating_lyrics"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "app.yukine.floating_lyrics.STOP"

        @JvmStatic
        fun canShow(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        @JvmStatic
        fun start(context: Context) {
            if (!canShow(context)) {
                return
            }
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var lyricsView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!canShow(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!showOverlay()) {
            stopSelf()
            return
        }
        observeLyrics()
    }

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun showOverlay(): Boolean {
        if (!canShow(this)) {
            return false
        }
        val textView = TextView(this).apply {
            textSize = 14f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(0xDD1A1A1A.toInt())
            setTextColor(0xFFE0E0E0.toInt())
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = ResourcesCompat.getFont(this@FloatingLyricsService, R.font.noto_sans_cjk_sc_regular)
                ?: android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(80)
        }

        setupDrag(textView, params)
        try {
            windowManager?.addView(textView, params)
        } catch (_: RuntimeException) {
            return false
        }
        lyricsView = textView
        layoutParams = params
        return true
    }

    private fun setupDrag(view: TextView, params: WindowManager.LayoutParams) {
        var initialY = 0
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        lyricsView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: RuntimeException) {
            }
        }
        lyricsView = null
        layoutParams = null
    }

    private fun observeLyrics() {
        collectJob = scope.launch {
            FloatingLyricsPublisher.state.collectLatest { state ->
                lyricsView?.apply {
                    text = state.activeLine.ifBlank { state.trackTitle }
                    visibility = if (state.activeLine.isNotBlank()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Lyrics",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows lyrics overlay" }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingLyricsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        val notification = builder
            .setSmallIcon(app.yukine.R.drawable.ic_stat_echo)
            .setContentTitle("Floating Lyrics")
            .setContentText("Showing synced lyrics")
            .setContentIntent(contentIntent)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, app.yukine.R.drawable.ic_notif_pause),
                    "Stop",
                    stopIntent
                ).build()
            )
        } else {
            @Suppress("DEPRECATION")
            notification.addAction(app.yukine.R.drawable.ic_notif_pause, "Stop", stopIntent)
        }
        return notification.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
