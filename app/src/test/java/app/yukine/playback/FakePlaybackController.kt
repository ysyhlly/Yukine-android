package app.yukine.playback

import app.yukine.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackController 的 Fake 实现，用于单元测试。
 *
 * 提供可控的播放状态和队列，方便测试 ViewModel 逻辑。
 *
 * 使用示例：
 * ```
 * val fake = FakePlaybackController()
 * fake.setPlaying(true)
 * fake.setQueue(listOf(track1, track2))
 * // 测试 ViewModel
 * ```
 */
class FakePlaybackController : PlaybackController {

    private val _state = MutableStateFlow(PlaybackStateSnapshot.empty())
    override val state: StateFlow<PlaybackStateSnapshot> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private var serviceConnected = true

    val commands = mutableListOf<String>()

    override fun isServiceConnected(): Boolean = serviceConnected

    fun setServiceConnected(connected: Boolean) {
        serviceConnected = connected
    }

    fun setState(snapshot: PlaybackStateSnapshot) {
        _state.value = snapshot
    }

    fun setQueue(tracks: List<Track>) {
        _queue.value = tracks
    }

    fun setPlaying(playing: Boolean) {
        val current = _state.value
        _state.value = PlaybackStateSnapshot(
            current.currentTrack,
            current.currentIndex,
            current.queueSize,
            current.positionMs,
            current.durationMs,
            playing,
            current.preparing,
            current.errorMessage,
            current.shuffleEnabled,
            current.repeatMode,
            current.playbackSpeed,
            current.appVolume,
            current.sleepTimerRemainingMs,
            current.waveform,
            current.spectrum,
            current.realtimeBeat
        )
    }

    fun setCurrentTrack(track: Track?) {
        val current = _state.value
        _state.value = PlaybackStateSnapshot(
            track,
            current.currentIndex,
            current.queueSize,
            current.positionMs,
            current.durationMs,
            current.playing,
            current.preparing,
            current.errorMessage,
            current.shuffleEnabled,
            current.repeatMode,
            current.playbackSpeed,
            current.appVolume,
            current.sleepTimerRemainingMs,
            current.waveform,
            current.spectrum,
            current.realtimeBeat
        )
    }

    fun setPosition(positionMs: Long, durationMs: Long = _state.value.durationMs) {
        val current = _state.value
        _state.value = PlaybackStateSnapshot(
            current.currentTrack,
            current.currentIndex,
            current.queueSize,
            positionMs,
            durationMs,
            current.playing,
            current.preparing,
            current.errorMessage,
            current.shuffleEnabled,
            current.repeatMode,
            current.playbackSpeed,
            current.appVolume,
            current.sleepTimerRemainingMs,
            current.waveform,
            current.spectrum,
            current.realtimeBeat
        )
    }

    private fun setShuffleEnabledInternal(enabled: Boolean) {
        val current = _state.value
        _state.value = PlaybackStateSnapshot(
            current.currentTrack,
            current.currentIndex,
            current.queueSize,
            current.positionMs,
            current.durationMs,
            current.playing,
            current.preparing,
            current.errorMessage,
            enabled,
            current.repeatMode,
            current.playbackSpeed,
            current.appVolume,
            current.sleepTimerRemainingMs,
            current.waveform,
            current.spectrum,
            current.realtimeBeat
        )
    }

    private fun setRepeatModeInternal(mode: Int) {
        val current = _state.value
        _state.value = PlaybackStateSnapshot(
            current.currentTrack,
            current.currentIndex,
            current.queueSize,
            current.positionMs,
            current.durationMs,
            current.playing,
            current.preparing,
            current.errorMessage,
            current.shuffleEnabled,
            mode,
            current.playbackSpeed,
            current.appVolume,
            current.sleepTimerRemainingMs,
            current.waveform,
            current.spectrum,
            current.realtimeBeat
        )
    }

    override suspend fun playQueue(tracks: List<Track>, index: Int) {
        commands.add("playQueue(${tracks.size} tracks, index=$index)")
        _queue.value = tracks
        if (index in tracks.indices) {
            setCurrentTrack(tracks[index])
            setPlaying(true)
        }
    }

    override suspend fun togglePlayPause() {
        commands.add("togglePlayPause")
        setPlaying(!_state.value.playing)
    }

    override suspend fun pause() {
        commands.add("pause")
        setPlaying(false)
    }

    override suspend fun play() {
        commands.add("play")
        setPlaying(true)
    }

    override suspend fun skipToNext() {
        commands.add("skipToNext")
        val currentQueue = _queue.value
        val currentTrack = _state.value.currentTrack
        val currentIndex = currentQueue.indexOfFirst { it.id == currentTrack?.id }
        if (currentIndex >= 0 && currentIndex < currentQueue.size - 1) {
            setCurrentTrack(currentQueue[currentIndex + 1])
        }
    }

    override suspend fun skipToPrevious() {
        commands.add("skipToPrevious")
        val currentQueue = _queue.value
        val currentTrack = _state.value.currentTrack
        val currentIndex = currentQueue.indexOfFirst { it.id == currentTrack?.id }
        if (currentIndex > 0) {
            setCurrentTrack(currentQueue[currentIndex - 1])
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        commands.add("seekTo($positionMs)")
        setPosition(positionMs)
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        commands.add("setShuffleEnabled($enabled)")
        setShuffleEnabledInternal(enabled)
    }

    override suspend fun cycleRepeatMode() {
        commands.add("cycleRepeatMode")
        val next = when (_state.value.repeatMode) {
            0 -> 1 // OFF -> ALL
            1 -> 2 // ALL -> ONE
            else -> 0 // ONE -> OFF
        }
        setRepeatModeInternal(next)
    }

    override suspend fun setRepeatMode(mode: Int) {
        commands.add("setRepeatMode($mode)")
        setRepeatModeInternal(mode)
    }

    override suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        commands.add("moveQueueItem($fromIndex, $toIndex)")
        val mutableQueue = _queue.value.toMutableList()
        if (fromIndex in mutableQueue.indices && toIndex in mutableQueue.indices) {
            val item = mutableQueue.removeAt(fromIndex)
            mutableQueue.add(toIndex, item)
            _queue.value = mutableQueue
        }
    }

    override suspend fun removeQueueItems(trackIds: Set<Long>) {
        commands.add("removeQueueItems(${trackIds.size} ids)")
        _queue.value = _queue.value.filterNot { it.id in trackIds }
    }

    override suspend fun clearQueue() {
        commands.add("clearQueue")
        _queue.value = emptyList()
        setCurrentTrack(null)
        setPlaying(false)
    }

    override suspend fun appendToQueue(tracks: List<Track>) {
        commands.add("appendToQueue(${tracks.size} tracks)")
        _queue.value = _queue.value + tracks
    }

    override suspend fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
        commands.add("replaceCurrentTrackAndResume(${track.id}, $positionMs)")
        setCurrentTrack(track)
        setPosition(positionMs)
    }

    override suspend fun startSleepTimer(minutes: Int) {
        commands.add("startSleepTimer($minutes)")
    }

    override suspend fun cancelSleepTimer() {
        commands.add("cancelSleepTimer")
    }

    override suspend fun precacheTrack(track: Track) {
        commands.add("precacheTrack(${track.id})")
    }

    override suspend fun replaceQueuedTrack(track: Track) {
        commands.add("replaceQueuedTrack(${track.id})")
        _queue.value = _queue.value.map { if (it.id == track.id) track else it }
    }

    override suspend fun replaceQueuedTrackById(oldTrackId: Long, updatedTrack: Track) {
        commands.add("replaceQueuedTrackById($oldTrackId, ${updatedTrack.id})")
        _queue.value = _queue.value.map { if (it.id == oldTrackId) updatedTrack else it }
    }

    fun clearCommands() {
        commands.clear()
    }

    fun hasCommand(command: String): Boolean {
        return commands.any { it.contains(command) }
    }
}
