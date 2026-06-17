package app.echo.next

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
import app.echo.next.ui.LyricUiLine
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
    val activeLine: String = "",
    val visible: Boolean = false
)

/**
 * Static publisher for lyrics state that the floating service observes.
 * Updated by NowPlayingViewModel / MainStatePublisher whenever lyrics change.
 */
object FloatingLyricsPublisher {
    private val _state = MutableStateFlow(FloatingLyricsState())
    val state: StateFlow<FloatingLyricsState> = _state.asStateFlow()

    @JvmStatic
    fun update(trackTitle: String, lyrics: List<LyricUiLine>) {
        val active = lyrics.firstOrNull { it.active }?.text
            ?: lyrics.firstOrNull()?.text
            ?: ""
        _state.value = FloatingLyricsState(
            trackTitle = trackTitle,
            activeLine = active,
            visible = active.isNotBlank()
        )
    }

    @JvmStatic
    fun clear() {
        _state.value = FloatingLyricsState()
    }
}

class FloatingLyricsService : Service() {

    companion object {
        private const val CHANNEL_ID = "echo_floating_lyrics"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "app.echo.next.floating_lyrics.STOP"

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
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
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
            .setSmallIcon(app.echo.next.R.drawable.ic_stat_echo)
            .setContentTitle("Floating Lyrics")
            .setContentText("Showing synced lyrics")
            .setContentIntent(contentIntent)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, app.echo.next.R.drawable.ic_notif_pause),
                    "Stop",
                    stopIntent
                ).build()
            )
        } else {
            @Suppress("DEPRECATION")
            notification.addAction(app.echo.next.R.drawable.ic_notif_pause, "Stop", stopIntent)
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
