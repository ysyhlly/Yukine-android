package app.yukine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.AudioEffectSettings
import app.yukine.playback.PlaybackCommands
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.MutablePlaybackReadModel
import app.yukine.playback.service.PlaybackServiceActions
import app.yukine.playback.state.PlaybackStateListener
import kotlinx.coroutines.flow.StateFlow

internal class PlaybackReadModelStateListener(
    private val publish: (PlaybackStateSnapshot) -> Unit,
    private val downstream: PlaybackStateListener
) : PlaybackStateListener {
    override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
        publish(snapshot)
        downstream.onPlaybackStateChanged(snapshot)
    }

    override fun onPlaybackBuffering(snapshot: PlaybackStateSnapshot) {
        publish(snapshot)
        downstream.onPlaybackBuffering(snapshot)
    }
}

internal class PlaybackServiceConnectionController(
    private val context: Context,
    private val playbackStateListener: PlaybackStateListener,
    private val listener: Listener,
    private val serviceStarter: NowPlayingPlaybackServiceStarter,
    private val commandQueue: PlaybackServiceCommandQueue
) : PlaybackReadModel, PlaybackCommands, SettingsPlaybackServicePort {
    interface Listener {
        fun onPlaybackServiceConnected(service: PlaybackServiceHostPort)

        fun onPlaybackServiceDisconnected()
    }

    private var service: PlaybackServiceHostPort? = null
    private var bound: Boolean = false
    private var appVisible: Boolean = false

    private val readModel = MutablePlaybackReadModel()
    override val state: StateFlow<PlaybackStateSnapshot> = readModel.state
    override val queue: StateFlow<PlaybackQueueSnapshot> = readModel.queue
    override val connection: StateFlow<PlaybackConnectionState> = readModel.connection

    private val readModelListener =
        PlaybackReadModelStateListener(::publishReadModel, playbackStateListener)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val nextService: PlaybackServiceHostPort = (binder as EchoPlaybackService.LocalBinder).service
            if (service != null && service !== nextService) {
                service?.unregisterListener(readModelListener)
            }
            service = nextService
            bound = true
            readModel.markConnected()
            nextService.registerListener(readModelListener)
            nextService.snapshot()?.let(::publishReadModel)
            commandQueue.flush(nextService)
            nextService.setAppVisible(appVisible)
            listener.onPlaybackServiceConnected(nextService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }

        override fun onBindingDied(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }

        override fun onNullBinding(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }
    }

    fun bind() {
        if (bound || connection.value == PlaybackConnectionState.Connecting) {
            return
        }
        readModel.markConnecting()
        if (!context.bindService(
                Intent(context, EchoPlaybackService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        ) {
            readModel.clear()
        }
    }

    fun release() {
        service?.unregisterListener(readModelListener)
        if (bound) {
            context.unbindService(serviceConnection)
        }
        service = null
        bound = false
        clearPublishedState()
    }

    fun isBound(): Boolean {
        return bound
    }

    fun isConnected(): Boolean = service != null && connection.value == PlaybackConnectionState.Connected

    fun queueSnapshot(): List<Track> = queue.value.tracks

    fun queueSize(): Int = queue.value.tracks.size

    fun queueTrackAt(index: Int): Track? = queue.value.tracks.getOrNull(index)

    fun realtimeBeat(): Float = service?.realtimeBeat() ?: 0f

    fun realtimeBands(): FloatArray = service?.realtimeBands() ?: FloatArray(0)

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        service?.setAppVisible(visible)
    }

    private fun clearServiceReference() {
        val disconnectedService = service
        service = null
        bound = false
        disconnectedService?.unregisterListener(readModelListener)
        clearPublishedState()
    }

    private fun publishReadModel(snapshot: PlaybackStateSnapshot) {
        readModel.publish(snapshot) { service?.queueSnapshot().orEmpty() }
    }

    private fun clearPublishedState() {
        readModel.clear()
    }

    override fun skipToPrevious() {
        service?.skipToPrevious() ?: serviceStarter.startPlaybackService(PlaybackServiceActions.PREVIOUS)
    }

    override fun skipToNext() {
        service?.skipToNext() ?: serviceStarter.startPlaybackService(PlaybackServiceActions.NEXT)
    }

    override fun seekTo(positionMs: Long) = executeOrQueue { it.seekTo(positionMs) }

    override fun removeTracksById(trackIds: Set<Long>) =
        executeOrQueue { it.removeTracksById(trackIds) }

    override fun clearQueue() = executeOrQueue { it.clearQueue() }

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) =
        executeOrQueue { it.moveQueueTrack(fromIndex, toIndex) }

    override fun replaceQueuedTrack(updated: Track) =
        executeOrQueue { it.replaceQueuedTrack(updated) }

    override fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri) =
        executeOrQueue { it.updateQueuedTrackArtwork(trackId, artworkUri) }

    override fun replaceQueuedTracks(updated: List<Track>) =
        executeOrQueue { it.replaceQueuedTracks(updated) }

    override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) =
        executeOrQueue { it.replaceQueuedTrackById(oldTrackId, updated) }

    override fun retainTracksById(trackIds: Set<Long>) =
        executeOrQueue { it.retainTracksById(trackIds) }

    override fun warmPlaybackTrack(track: Track) = executeOrQueue { it.warmPlaybackTrack(track) }

    override fun appendToQueue(tracks: List<Track>) = executeOrQueue { it.appendToQueue(tracks) }

    override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) =
        executeOrQueue { it.replaceCurrentTrackAndResume(track, positionMs) }

    override fun replaceCurrentSourceAndResume(
        expectedTrackId: Long,
        track: Track,
        positionMs: Long
    ) = executeOrQueue { it.replaceCurrentSourceAndResume(expectedTrackId, track, positionMs) }

    override fun startSleepTimerMinutes(minutes: Int) =
        executeOrQueue { it.startSleepTimerMinutes(minutes) }

    override fun cancelSleepTimer() = executeOrQueue { it.cancelSleepTimer() }

    override fun playQueue(tracks: List<Track>, index: Int) {
        serviceStarter.startPlaybackService(null)
        executeOrQueue { it.playQueue(tracks, index) }
    }

    override fun pause() = executeOrQueue { it.pause() }

    override fun play() {
        serviceStarter.startPlaybackService(null)
        executeOrQueue { it.play() }
    }

    override fun setShuffleEnabled(enabled: Boolean) =
        executeOrQueue { it.setShuffleEnabled(enabled) }

    override fun cycleRepeatMode() = executeOrQueue { it.cycleRepeatMode() }

    override fun setRepeatMode(repeatMode: Int) =
        executeOrQueue { it.setRepeatMode(repeatMode) }

    override fun setPlaybackSpeed(speed: Float) {
        service?.setPlaybackSpeed(speed)
    }

    override fun setAppVolume(volume: Float) {
        service?.setAppVolume(volume)
    }

    override fun setAudioExclusiveEnabled(enabled: Boolean) {
        service?.setAudioExclusiveEnabled(enabled)
    }

    override fun setBitPerfectEnabled(enabled: Boolean) {
        service?.setBitPerfectEnabled(enabled)
    }

    override fun setUsbExclusiveEnabled(enabled: Boolean) {
        service?.setUsbExclusiveEnabled(enabled)
    }

    override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        service?.applyAudioEffectSettings(settings)
    }

    override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        service?.setStatusBarLyricsEnabled(enabled)
    }

    override fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        service?.setSystemMediaLyricsTitleEnabled(enabled)
    }

    override fun setPlaybackRestoreEnabled(enabled: Boolean) {
        service?.setPlaybackRestoreEnabled(enabled)
    }

    override fun setReplayGainEnabled(enabled: Boolean) {
        service?.setReplayGainEnabled(enabled)
    }

    private fun executeOrQueue(command: PlaybackServiceCommand) {
        commandQueue.executeOrEnqueue({ service }, command)
    }
}
