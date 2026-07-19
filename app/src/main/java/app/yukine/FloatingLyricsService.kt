package app.yukine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.service.PlaybackServiceActions
import app.yukine.streaming.StreamingPlaybackAdapter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        internal const val ACTION_SHOW = "app.yukine.floating_lyrics.SHOW"
        internal const val ACTION_HIDE_SESSION = "app.yukine.floating_lyrics.HIDE_SESSION"
        internal const val ACTION_UNLOCK = "app.yukine.floating_lyrics.UNLOCK"
        internal const val ACTION_REFRESH_SETTINGS = "app.yukine.floating_lyrics.REFRESH_SETTINGS"
        internal const val ACTION_RESET_LAYOUT = "app.yukine.floating_lyrics.RESET_LAYOUT"
        internal const val EXTRA_OPEN_SETTINGS = "app.yukine.extra.OPEN_FLOATING_LYRICS_SETTINGS"

        @Volatile
        private var lastRuntimeStatus = FloatingLyricsRuntimeStatus.Disabled

        @JvmStatic
        fun canShow(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        @JvmStatic
        fun start(context: Context, action: String? = null): Boolean {
            if (!canShow(context)) {
                lastRuntimeStatus = FloatingLyricsRuntimeStatus.PermissionRequired
                return false
            }
            val intent = Intent(context, FloatingLyricsService::class.java).setAction(action)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to start floating lyrics service", error)
                lastRuntimeStatus = FloatingLyricsRuntimeStatus.Failed
                false
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            lastRuntimeStatus = FloatingLyricsRuntimeStatus.Disabled
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }

        @JvmStatic
        fun show(context: Context): Boolean = start(context, ACTION_SHOW)

        @JvmStatic
        fun unlock(context: Context): Boolean = start(context, ACTION_UNLOCK)

        @JvmStatic
        fun refreshSettings(context: Context): Boolean = start(context, ACTION_REFRESH_SETTINGS)

        @JvmStatic
        fun resetLayout(context: Context): Boolean = start(context, ACTION_RESET_LAYOUT)

        @JvmStatic
        fun runtimeStatus(): FloatingLyricsRuntimeStatus = lastRuntimeStatus

        private const val TAG = "FloatingLyricsService"
        private const val PERMISSION_CHECK_INTERVAL_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private lateinit var settingsStore: FloatingLyricsOverlaySettingsStore
    private lateinit var windowController: FloatingLyricsWindowController
    private lateinit var notificationOwner: FloatingLyricsNotificationOwner
    private lateinit var artworkLoader: FloatingLyricsArtworkLoader
    private var overlaySettings = FloatingLyricsOverlaySettings()
    private var overlayView: FloatingLyricsOverlayView? = null
    private var presentation: FloatingLyricsPresentation =
        FloatingLyricsPresentation.WaitingForLyrics
    private var latestState = FloatingLyricsState()
    private var sessionInteraction = FloatingLyricsInteraction.Interactive
    private var disablingAfterPermissionLoss = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = FloatingLyricsOverlaySettingsStore(this)
        overlaySettings = settingsStore.load()
        notificationOwner = FloatingLyricsNotificationOwner(this)
        artworkLoader = FloatingLyricsArtworkLoader(this)
        windowController = FloatingLyricsWindowController(this, ::handleWindowFailure)
        notificationOwner.createChannel()
        if (!startForegroundSafely()) {
            lastRuntimeStatus = FloatingLyricsRuntimeStatus.Failed
            stopSelf()
            return
        }
        if (!canShow(this)) {
            handlePermissionLoss()
            return
        }
        lastRuntimeStatus = FloatingLyricsRuntimeStatus.Waiting
        observeLyrics()
        observePermission()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                presentation = FloatingLyricsOverlayReducer.reduce(
                    presentation,
                    FloatingLyricsOverlayAction.Show
                )
                reconcileLyrics(latestState)
            }
            ACTION_HIDE_SESSION -> handleAction(FloatingLyricsOverlayAction.HideSession)
            ACTION_UNLOCK -> handleAction(FloatingLyricsOverlayAction.Unlock)
            ACTION_REFRESH_SETTINGS -> {
                overlaySettings = settingsStore.load()
                applyOverlaySettings()
            }
            ACTION_RESET_LAYOUT -> {
                overlaySettings = settingsStore.reset()
                applyOverlaySettings()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowController.refreshBounds()
    }

    override fun onDestroy() {
        windowController.remove()
        overlayView = null
        collectJob?.cancel()
        scope.cancel()
        if (!disablingAfterPermissionLoss) {
            lastRuntimeStatus = FloatingLyricsRuntimeStatus.Disabled
        }
        super.onDestroy()
    }

    private fun startForegroundSafely(): Boolean = try {
        val notification = notificationOwner.build(presentation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FloatingLyricsNotificationOwner.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FloatingLyricsNotificationOwner.NOTIFICATION_ID, notification)
        }
        true
    } catch (error: RuntimeException) {
        Log.w(TAG, "Unable to enter foreground", error)
        false
    }

    private fun observeLyrics() {
        collectJob = scope.launch {
            FloatingLyricsPublisher.state.collectLatest { state ->
                latestState = state
                reconcileLyrics(state)
            }
        }
    }

    private fun observePermission() {
        scope.launch {
            while (isActive && !disablingAfterPermissionLoss) {
                delay(PERMISSION_CHECK_INTERVAL_MS)
                if (!canShow(this@FloatingLyricsService)) {
                    handlePermissionLoss()
                }
            }
        }
    }

    private fun reconcileLyrics(state: FloatingLyricsState) {
        if (!canShow(this)) {
            handlePermissionLoss()
            return
        }
        presentation = FloatingLyricsOverlayReducer.onLyricsAvailability(
            presentation,
            state.activeLine.isNotBlank() && state.visible
        )
        val availablePresentation = presentation
        if (availablePresentation is FloatingLyricsPresentation.Visible &&
            availablePresentation.interaction != sessionInteraction
        ) {
            presentation = availablePresentation.copy(interaction = sessionInteraction)
        }
        when (val current = presentation) {
            FloatingLyricsPresentation.WaitingForLyrics -> {
                removeOverlay()
                lastRuntimeStatus = FloatingLyricsRuntimeStatus.Waiting
            }
            FloatingLyricsPresentation.HiddenByUser -> {
                removeOverlay()
                lastRuntimeStatus = FloatingLyricsRuntimeStatus.Hidden
            }
            is FloatingLyricsPresentation.Visible -> {
                if (!ensureOverlay(current)) {
                    lastRuntimeStatus = FloatingLyricsRuntimeStatus.Failed
                    return
                }
                lastRuntimeStatus = FloatingLyricsRuntimeStatus.Visible
                renderVisibleState(state, current)
            }
        }
        notificationOwner.notify(presentation)
    }

    private fun ensureOverlay(visible: FloatingLyricsPresentation.Visible): Boolean {
        val view = overlayView ?: FloatingLyricsOverlayView(
            this,
            overlaySettings,
            ::handleAction
        ).also { overlayView = it }
        view.applySettings(overlaySettings)
        view.renderPresentation(visible, animate = false)
        return windowController.show(view.root, overlaySettings, visible.interaction)
    }

    private fun renderVisibleState(
        state: FloatingLyricsState,
        visible: FloatingLyricsPresentation.Visible
    ) {
        val view = overlayView ?: return
        view.renderPresentation(visible)
        view.render(state, artwork = null)
        scope.launch {
            val artwork = artworkLoader.load(state.albumArtUri, dp(96))
            if (latestState.trackId == state.trackId && presentation is FloatingLyricsPresentation.Visible) {
                overlayView?.render(latestState, artwork)
            }
        }
    }

    private fun handleAction(action: FloatingLyricsOverlayAction) {
        when (action) {
            is FloatingLyricsOverlayAction.DragBy -> {
                windowController.moveBy(action.deltaX, action.deltaY)
                return
            }
            FloatingLyricsOverlayAction.DragFinished -> {
                val (x, y) = windowController.currentPositionFractions()
                updateOverlaySettings { it.copy(positionXFraction = x, positionYFraction = y) }
                return
            }
            FloatingLyricsOverlayAction.PlayPause -> {
                dispatchPlaybackAction(
                    if (latestState.playing) PlaybackServiceActions.PAUSE else PlaybackServiceActions.PLAY
                )
                return
            }
            FloatingLyricsOverlayAction.Previous -> {
                dispatchPlaybackAction(PlaybackServiceActions.PREVIOUS)
                return
            }
            FloatingLyricsOverlayAction.Next -> {
                dispatchPlaybackAction(PlaybackServiceActions.NEXT)
                return
            }
            is FloatingLyricsOverlayAction.UpdateBackgroundOpacity -> {
                updateOverlaySettings {
                    it.copy(
                        backgroundOpacityPercent = action.percent,
                        transparentBackground = false
                    )
                }
                return
            }
            FloatingLyricsOverlayAction.OpenSettings -> {
                openApplicationSettings()
                return
            }
            FloatingLyricsOverlayAction.RequestClickThrough,
            FloatingLyricsOverlayAction.CancelClickThrough -> return
            else -> Unit
        }
        presentation = FloatingLyricsOverlayReducer.reduce(presentation, action)
        when (action) {
            FloatingLyricsOverlayAction.ConfirmClickThrough ->
                sessionInteraction = FloatingLyricsInteraction.ClickThrough
            FloatingLyricsOverlayAction.Unlock ->
                sessionInteraction = FloatingLyricsInteraction.Interactive
            else -> Unit
        }
        reconcileLyrics(latestState)
    }

    private fun updateOverlaySettings(
        transform: (FloatingLyricsOverlaySettings) -> FloatingLyricsOverlaySettings
    ) {
        val next = transform(overlaySettings).normalized()
        if (next == overlaySettings) return
        overlaySettings = next
        settingsStore.save(next)
        applyOverlaySettings()
    }

    private fun applyOverlaySettings() {
        overlayView?.applySettings(overlaySettings)
        val visible = presentation as? FloatingLyricsPresentation.Visible
        if (visible != null) {
            windowController.update(overlaySettings, visible.interaction)
        }
        notificationOwner.notify(presentation)
    }

    private fun removeOverlay() {
        windowController.remove()
        overlayView = null
    }

    private fun dispatchPlaybackAction(action: String) {
        val intent = Intent(this, EchoPlaybackService::class.java).setAction(action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to dispatch playback action $action", error)
        }
    }

    private fun openApplicationSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_SETTINGS, true)
        runCatching(::startActivity)
            .onFailure { Log.w(TAG, "Unable to open floating lyrics settings", it) }
    }

    private fun handleWindowFailure(error: Throwable) {
        if (error is SecurityException || !canShow(this)) {
            handlePermissionLoss()
        } else {
            lastRuntimeStatus = FloatingLyricsRuntimeStatus.Failed
        }
    }

    private fun handlePermissionLoss() {
        if (disablingAfterPermissionLoss) return
        disablingAfterPermissionLoss = true
        lastRuntimeStatus = FloatingLyricsRuntimeStatus.PermissionRequired
        removeOverlay()
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    MusicLibraryRepository(this@FloatingLyricsService, StreamingPlaybackAdapter)
                        .saveFloatingLyricsEnabled(false)
                }.onFailure { Log.w(TAG, "Unable to disable floating lyrics preference", it) }
            }
            stopSelf()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
