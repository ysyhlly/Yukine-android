package app.yukine.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.yukine.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackController 的 Service 实现，封装 EchoPlaybackService 绑定和调用细节。
 *
 * 职责：
 * - 管理服务生命周期（bind/unbind）
 * - 监听播放状态变化并发布到 StateFlow
 * - 将播放命令转发到 Service
 * - 为 ViewModel 提供统一的播放控制接口
 *
 * 使用方式：
 * ```
 * val controller = PlaybackServiceController(context)
 * controller.bind()
 * // ViewModel 使用 controller.state 和 controller.queue
 * controller.playQueue(tracks, 0)
 * ```
 */
class PlaybackServiceController(
    private val context: Context
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackStateSnapshot.empty())
    override val state: StateFlow<PlaybackStateSnapshot> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    @Volatile
    private var service: EchoPlaybackService? = null

    @Volatile
    private var bound = false

    private val stateListener = PlaybackStateListener { snapshot ->
        _state.value = snapshot ?: PlaybackStateSnapshot.empty()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val nextService = (binder as EchoPlaybackService.LocalBinder).service
            val previousService = service
            if (previousService != null && previousService !== nextService) {
                previousService.unregisterListener(stateListener)
            }
            service = nextService
            bound = true
            nextService.registerListener(stateListener)

            // 初始化状态
            val snapshot = nextService.snapshot()
            _state.value = snapshot ?: PlaybackStateSnapshot.empty()
            _queue.value = nextService.queueSnapshot() ?: emptyList()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearServiceReference()
            _state.value = PlaybackStateSnapshot.empty()
            _queue.value = emptyList()
        }

        override fun onBindingDied(name: ComponentName) {
            clearServiceReference()
            _state.value = PlaybackStateSnapshot.empty()
            _queue.value = emptyList()
        }

        override fun onNullBinding(name: ComponentName) {
            clearServiceReference()
            _state.value = PlaybackStateSnapshot.empty()
            _queue.value = emptyList()
        }
    }

    /**
     * 绑定播放服务。应在 Activity onCreate 时调用。
     */
    fun bind() {
        context.bindService(
            Intent(context, EchoPlaybackService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    /**
     * 解绑播放服务。应在 Activity onDestroy 时调用。
     */
    fun unbind() {
        service?.unregisterListener(stateListener)
        if (bound) {
            context.unbindService(connection)
        }
        service = null
        bound = false
    }

    override fun isServiceConnected(): Boolean {
        return bound && service != null
    }

    override suspend fun playQueue(tracks: List<Track>, index: Int) {
        service?.playQueue(tracks, index)
        updateQueueState()
    }

    override suspend fun togglePlayPause() {
        val currentService = service ?: return
        if (currentService.snapshot()?.playing == true) {
            currentService.pause()
        } else {
            currentService.play()
        }
    }

    override suspend fun pause() {
        service?.pause()
    }

    override suspend fun play() {
        service?.play()
    }

    override suspend fun skipToNext() {
        service?.skipToNext()
        updateQueueState()
    }

    override suspend fun skipToPrevious() {
        service?.skipToPrevious()
        updateQueueState()
    }

    override suspend fun seekTo(positionMs: Long) {
        service?.seekTo(positionMs)
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        service?.setShuffleEnabled(enabled)
        updateQueueState()
    }

    override suspend fun cycleRepeatMode() {
        service?.cycleRepeatMode()
    }

    override suspend fun setRepeatMode(mode: Int) {
        service?.setRepeatMode(mode)
    }

    override suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        service?.moveQueueTrack(fromIndex, toIndex)
        updateQueueState()
    }

    override suspend fun removeQueueItems(trackIds: Set<Long>) {
        service?.removeTracksById(trackIds)
        updateQueueState()
    }

    override suspend fun clearQueue() {
        service?.clearQueue()
        updateQueueState()
    }

    override suspend fun appendToQueue(tracks: List<Track>) {
        service?.appendToQueue(tracks)
        updateQueueState()
    }

    override suspend fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
        service?.replaceCurrentTrackAndResume(track, positionMs)
        updateQueueState()
    }

    override suspend fun startSleepTimer(minutes: Int) {
        service?.startSleepTimerMinutes(minutes)
    }

    override suspend fun cancelSleepTimer() {
        service?.cancelSleepTimer()
    }

    override suspend fun precacheTrack(track: Track) {
        service?.precacheTrack(track)
    }

    override suspend fun replaceQueuedTrack(track: Track) {
        service?.replaceQueuedTrack(track)
        updateQueueState()
    }

    override suspend fun replaceQueuedTrackById(oldTrackId: Long, updatedTrack: Track) {
        service?.replaceQueuedTrackById(oldTrackId, updatedTrack)
        updateQueueState()
    }

    /**
     * 更新队列状态。在队列可能变化的操作后调用。
     */
    private fun updateQueueState() {
        val currentService = service
        if (currentService != null) {
            _queue.value = currentService.queueSnapshot() ?: emptyList()
        }
    }

    private fun clearServiceReference() {
        val disconnectedService = service
        service = null
        bound = false
        disconnectedService?.unregisterListener(stateListener)
    }
}
