package app.yukine.playback.manager

import android.net.Uri
import app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL
import app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedList
import java.util.Random

internal class PlaybackQueueManager(
    private val queueStore: PlaybackQueueStore,
    private val queueProvider: QueueProvider,
    private val queuePlaybackActions: QueuePlaybackActions,
    private val playbackPositionManager: PlaybackPositionManager? = null,
    private val streamingRestoreProvider: StreamingRestoreProvider,
    private val mirroredQueuePlayer: MirroredQueuePlayer,
    private val playbackRuntimeStateManager: PlaybackRuntimeStateManager? = null,
    private val playbackTransitionStateManager: PlaybackTransitionStateManager? = null,
    private val random: Random = Random()
) {
    private var playbackRestoreEnabled = true
    private var currentIndex = -1

    interface QueueProvider {
        fun queue(): MutableList<Track>
    }

    interface QueuePlaybackActions {
        fun isPlaying(): Boolean
        fun prepareCurrent(playWhenReady: Boolean)
        fun publishState()
        fun stopAndClear()
    }

    interface StreamingRestoreProvider {
        fun restoredTrackFor(track: Track): Track?
        fun restoreForDataPath(dataPath: String?)
    }

    interface MirroredQueuePlayer {
        fun matchesCurrentQueue(): Boolean
        fun seekTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean
    }

    data class CurrentTrackReplacementRecovery(
        val track: Track,
        val restoredPositionMs: Long,
        val playWhenReady: Boolean
    )

    constructor(queueStore: PlaybackQueueStore, queueProvider: QueueProvider) : this(
        queueStore,
        queueProvider,
        NoopQueuePlaybackActions,
        null,
        NoopStreamingRestoreProvider,
        NoopMirroredQueuePlayer,
        null,
        null,
        Random()
    )

    constructor(
        queueStore: PlaybackQueueStore,
        queueProvider: QueueProvider,
        playbackPositionManager: PlaybackPositionManager?
    ) : this(queueStore, queueProvider, NoopQueuePlaybackActions, playbackPositionManager, NoopStreamingRestoreProvider, NoopMirroredQueuePlayer, null, null, Random())

    constructor(
        queueStore: PlaybackQueueStore,
        queueProvider: QueueProvider,
        playbackPositionManager: PlaybackPositionManager?,
        streamingRestoreProvider: StreamingRestoreProvider
    ) : this(queueStore, queueProvider, NoopQueuePlaybackActions, playbackPositionManager, streamingRestoreProvider, NoopMirroredQueuePlayer, null, null, Random())

    constructor(
        queueStore: PlaybackQueueStore,
        queueProvider: QueueProvider,
        playbackPositionManager: PlaybackPositionManager?,
        streamingRestoreProvider: StreamingRestoreProvider,
        playbackRuntimeStateManager: PlaybackRuntimeStateManager?,
        playbackTransitionStateManager: PlaybackTransitionStateManager?
    ) : this(
        queueStore,
        queueProvider,
        NoopQueuePlaybackActions,
        playbackPositionManager,
        streamingRestoreProvider,
        NoopMirroredQueuePlayer,
        playbackRuntimeStateManager,
        playbackTransitionStateManager,
        Random()
    )

    constructor(
        queueStore: PlaybackQueueStore,
        queueProvider: QueueProvider,
        playbackPositionManager: PlaybackPositionManager?,
        streamingRestoreProvider: StreamingRestoreProvider,
        mirroredQueuePlayer: MirroredQueuePlayer,
        playbackRuntimeStateManager: PlaybackRuntimeStateManager?,
        playbackTransitionStateManager: PlaybackTransitionStateManager?
    ) : this(
        queueStore,
        queueProvider,
        NoopQueuePlaybackActions,
        playbackPositionManager,
        streamingRestoreProvider,
        mirroredQueuePlayer,
        playbackRuntimeStateManager,
        playbackTransitionStateManager,
        Random()
    )

    constructor(
        queueStore: PlaybackQueueStore,
        queueProvider: QueueProvider,
        queuePlaybackActions: QueuePlaybackActions,
        playbackPositionManager: PlaybackPositionManager?,
        streamingRestoreProvider: StreamingRestoreProvider,
        mirroredQueuePlayer: MirroredQueuePlayer,
        playbackRuntimeStateManager: PlaybackRuntimeStateManager?,
        playbackTransitionStateManager: PlaybackTransitionStateManager?
    ) : this(
        queueStore,
        queueProvider,
        queuePlaybackActions,
        playbackPositionManager,
        streamingRestoreProvider,
        mirroredQueuePlayer,
        playbackRuntimeStateManager,
        playbackTransitionStateManager,
        Random()
    )

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        playbackRestoreEnabled = enabled
    }

    fun loadRestoredQueue(): PlaybackQueueState {
        return queueStore.load()
    }

    fun currentIndex(): Int {
        return currentIndex
    }

    fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    fun clampCurrentIndex(queueSize: Int): Int {
        if (queueSize <= 0) {
            return 0
        }
        return maxOf(0, minOf(currentIndex, queueSize - 1))
    }

    fun setClampedCurrentIndex(index: Int, queueSize: Int) {
        currentIndex = if (queueSize <= 0) {
            -1
        } else {
            maxOf(0, minOf(index, queueSize - 1))
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = queueProvider.queue()
        queue.clear()
        queue.addAll(tracks)
        setCurrentIndex(maxOf(0, minOf(startIndex, queue.size - 1)))
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        resetCurrentPlaybackPosition()
        if (startPositionMs >= 0L) {
            currentTrack()?.let { track ->
                setRestoredPosition(track.id, startPositionMs, explicit = true)
            }
        }
        savePlaybackResumeRequested(true)
        queuePlaybackActions.prepareCurrent(true)
    }

    fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = queueProvider.queue()
        val wasEmpty = queue.isEmpty()
        queue.addAll(tracks)
        if (currentIndex() < 0 || currentIndex() >= queue.size) {
            setCurrentIndex(0)
        }
        persistQueue()
        if (wasEmpty) {
            clearErrorMessage()
            clearLastMarkedTrack()
            clearRestoredPosition()
            resetCurrentPlaybackPosition()
            savePlaybackResumeRequested(true)
            queuePlaybackActions.prepareCurrent(true)
            return
        }
        queuePlaybackActions.publishState()
    }

    fun playFirstQueuedTrack() {
        if (queueProvider.queue().isEmpty()) {
            return
        }
        setCurrentIndex(0)
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        resetCurrentPlaybackPosition()
        queuePlaybackActions.prepareCurrent(true)
    }

    fun advanceQueueIndexToNext() {
        val queue = queueProvider.queue()
        if (queue.isEmpty()) {
            return
        }
        if (shuffleEnabled() && queue.size > 1) {
            var nextIndex = currentIndex()
            while (nextIndex == currentIndex()) {
                nextIndex = random.nextInt(queue.size)
            }
            setCurrentIndex(nextIndex)
            return
        }
        if (currentIndex() >= queue.size - 1) {
            if (repeatMode() == REPEAT_OFF) {
                persistQueue()
                queuePlaybackActions.publishState()
                return
            }
            setCurrentIndex(0)
            return
        }
        setCurrentIndex(currentIndex() + 1)
    }

    fun skipToNextImmediately(): Boolean {
        persistPlaybackPosition()
        advanceQueueIndexToNext()
        if (reuseMirroredQueueAtCurrentIndex(true)) {
            return true
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        resetCurrentPlaybackPosition()
        savePlaybackResumeRequested(true)
        queuePlaybackActions.prepareCurrent(true)
        return false
    }

    fun skipToPrevious(): Boolean {
        val queue = queueProvider.queue()
        if (queue.isEmpty()) {
            return false
        }
        persistPlaybackPosition()
        setCurrentIndex(
            if (currentIndex() <= 0) queue.size - 1 else currentIndex() - 1
        )
        if (reuseMirroredQueueAtCurrentIndex(true)) {
            return true
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        resetCurrentPlaybackPosition()
        savePlaybackResumeRequested(true)
        queuePlaybackActions.prepareCurrent(true)
        return false
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        val queue = queueProvider.queue()
        if (queue.isEmpty() || fromIndex == toIndex || fromIndex < 0 || fromIndex >= queue.size) {
            return
        }
        val targetIndex = maxOf(0, minOf(toIndex, queue.size - 1))
        val current = currentTrack()
        val moved = queue.removeAt(fromIndex)
        queue.add(targetIndex, moved)
        if (current != null) {
            setCurrentIndex(indexOfTrackOccurrence(queue, current))
        } else {
            setCurrentIndex(maxOf(0, minOf(currentIndex(), queue.size - 1)))
        }
        persistQueue()
        persistPlaybackPosition()
        queuePlaybackActions.publishState()
    }

    fun removeTracksById(trackIds: Set<Long>) {
        val queue = queueProvider.queue()
        if (trackIds.isEmpty() || queue.isEmpty()) {
            return
        }
        val current = currentTrack()
        val removedCurrent = current != null && trackIds.contains(current.id)
        var removedBeforeCurrent = 0
        for (i in queue.indices) {
            if (i < currentIndex() && trackIds.contains(queue[i].id)) {
                removedBeforeCurrent++
            }
        }
        for (i in queue.size - 1 downTo 0) {
            if (trackIds.contains(queue[i].id)) {
                queue.removeAt(i)
            }
        }
        if (queue.isEmpty()) {
            queuePlaybackActions.stopAndClear()
            return
        }
        if (currentIndex() >= 0) {
            setCurrentIndex(maxOf(0, currentIndex() - removedBeforeCurrent))
        }
        if (currentIndex() >= queue.size) {
            setCurrentIndex(queue.size - 1)
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        if (removedCurrent) {
            resetCurrentPlaybackPosition()
            queuePlaybackActions.prepareCurrent(false)
        } else {
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
        }
    }

    fun retainTracksById(trackIdsToKeep: Set<Long>) {
        if (trackIdsToKeep.isEmpty() || queueProvider.queue().isEmpty()) {
            return
        }
        val trackIdsToRemove = HashSet<Long>()
        for (track in queueProvider.queue()) {
            if (!trackIdsToKeep.contains(track.id)) {
                trackIdsToRemove.add(track.id)
            }
        }
        removeTracksById(trackIdsToRemove)
    }

    fun replaceQueuedTrack(replacement: Track) {
        val queue = queueProvider.queue()
        if (queue.isEmpty()) {
            return
        }
        var replaced = false
        var replacedCurrent = false
        for (i in queue.indices) {
            if (queue[i].id == replacement.id) {
                if (i == currentIndex()) {
                    replacedCurrent = true
                }
                queue[i] = replacement
                replaced = true
            }
        }
        if (replaced) {
            clearErrorMessage()
            clearLastMarkedTrack()
            clearRestoredPosition()
            persistQueue()
            if (replacedCurrent) {
                resetCurrentPlaybackPosition()
                queuePlaybackActions.prepareCurrent(queuePlaybackActions.isPlaying() || preparing() || queueProvider.queue().isEmpty())
                return
            }
            queuePlaybackActions.publishState()
        }
    }

    fun replaceQueuedTrackById(oldTrackId: Long, replacement: Track) {
        if (queueProvider.queue().isEmpty()) {
            return
        }
        if (oldTrackId == replacement.id) {
            replaceQueuedTrack(replacement)
            return
        }
        val queue = queueProvider.queue()
        var targetAlreadyQueued = false
        for (track in queue) {
            if (track.id == replacement.id) {
                targetAlreadyQueued = true
                break
            }
        }
        if (targetAlreadyQueued) {
            replaceAndCollapseQueuedTrack(oldTrackId, replacement)
            return
        }
        var replaced = false
        var replacedCurrent = false
        for (i in queue.indices) {
            if (queue[i].id == oldTrackId) {
                queue[i] = replacement
                replaced = true
                if (i == currentIndex()) {
                    replacedCurrent = true
                }
            }
        }
        if (!replaced) {
            replaceQueuedTrack(replacement)
            return
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        if (replacedCurrent) {
            resetCurrentPlaybackPosition()
            queuePlaybackActions.prepareCurrent(queuePlaybackActions.isPlaying())
        } else {
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
        }
    }

    fun replaceCurrentTrackAndResume(replacement: Track?, positionMs: Long): CurrentTrackReplacementRecovery? {
        val queue = queueProvider.queue()
        if (replacement == null || currentIndex() < 0 || currentIndex() >= queue.size) {
            return null
        }
        val current = currentTrack()
        val resumePositionMs = maxOf(maxOf(0L, positionMs), playbackPositionMs())
        queue[currentIndex()] = replacement
        clearErrorMessage()
        if (current != null && current.contentUri == replacement.contentUri && current.dataPath == replacement.dataPath) {
            persistQueue()
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
            return null
        }
        clearLastMarkedTrack()
        val restoredPositionMs = setExplicitRestoredPosition(replacement, resumePositionMs)
        persistQueue()
        return CurrentTrackReplacementRecovery(replacement, restoredPositionMs, playWhenReady = true)
    }

    fun reuseMirroredQueueIfAvailable(playWhenReady: Boolean, startPositionMs: Long): Boolean {
        return reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs, resetCurrentPlaybackState = false)
    }

    fun reuseMirroredQueueAtCurrentIndex(playWhenReady: Boolean): Boolean {
        return reuseMirroredQueueIfAvailable(playWhenReady, 0L, resetCurrentPlaybackState = true)
    }

    private fun reuseMirroredQueueIfAvailable(
        playWhenReady: Boolean,
        startPositionMs: Long,
        resetCurrentPlaybackState: Boolean
    ): Boolean {
        val queue = queueProvider.queue()
        if (queue.isEmpty() || !mirroredQueuePlayer.matchesCurrentQueue()) {
            return false
        }
        val targetIndex = maxOf(0, minOf(currentIndex(), queue.size - 1))
        val track = currentTrack() ?: return false
        clearErrorMessage()
        clearLastMarkedTrack()
        if (resetCurrentPlaybackState) {
            clearRestoredPosition()
            persistQueue()
            resetCurrentPlaybackPosition()
        }
        streamingRestoreProvider.restoreForDataPath(track.dataPath)
        val startAtMs = maxOf(0L, startPositionMs)
        if (!mirroredQueuePlayer.seekTo(targetIndex, startAtMs, playWhenReady)) {
            return false
        }
        if (playWhenReady) {
            savePlaybackResumeRequested(true)
        }
        if (!resetCurrentPlaybackState && startPositionMs > 0L) {
            clearRestoredPosition()
        }
        queuePlaybackActions.publishState()
        return true
    }

    fun mirroredQueueTracksForPreparation(): List<Track>? {
        val queue = queueProvider.queue()
        if (queue.isEmpty() || currentTrack() == null) {
            return null
        }
        for (track in queue) {
            if (track.contentUri == null || track.contentUri.toString().isEmpty()) {
                return null
            }
        }
        val tracks = ArrayList<Track>(queue.size)
        for (track in queue) {
            streamingRestoreProvider.restoreForDataPath(track.dataPath)
            tracks.add(track)
        }
        return tracks
    }

    private fun replaceAndCollapseQueuedTrack(oldTrackId: Long, replacement: Track) {
        val queue = queueProvider.queue()
        var preferredIndex = -1
        if (currentIndex() >= 0 && currentIndex() < queue.size) {
            val currentId = queue[currentIndex()].id
            if (currentId == oldTrackId || currentId == replacement.id) {
                preferredIndex = currentIndex()
            }
        }
        if (preferredIndex < 0) {
            for (i in queue.indices) {
                val trackId = queue[i].id
                if (trackId == oldTrackId || trackId == replacement.id) {
                    preferredIndex = i
                    break
                }
            }
        }
        if (preferredIndex < 0) {
            return
        }

        var replaced = false
        var currentWasOldTrack = false
        val collapsedQueue = LinkedList<Track>()
        var collapsedCurrentIndex = -1
        for (i in queue.indices) {
            val track = queue[i]
            val isOldTrack = track.id == oldTrackId
            val isReplacementTrack = track.id == replacement.id
            if (isOldTrack) {
                replaced = true
                if (i == currentIndex()) {
                    currentWasOldTrack = true
                }
            }
            if (isOldTrack || isReplacementTrack) {
                if (i == preferredIndex) {
                    collapsedCurrentIndex = collapsedQueue.size
                    collapsedQueue.add(replacement)
                }
                continue
            }
            if (i == currentIndex()) {
                collapsedCurrentIndex = collapsedQueue.size
            }
            collapsedQueue.add(track)
        }
        if (!replaced) {
            return
        }
        queue.clear()
        queue.addAll(collapsedQueue)
        if (queue.isEmpty()) {
            queuePlaybackActions.stopAndClear()
            return
        }
        if (collapsedCurrentIndex >= 0) {
            setCurrentIndex(collapsedCurrentIndex)
        } else if (currentIndex() >= 0) {
            setCurrentIndex(minOf(currentIndex(), queue.size - 1))
        } else {
            setCurrentIndex(-1)
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        if (currentWasOldTrack) {
            resetCurrentPlaybackPosition()
            queuePlaybackActions.prepareCurrent(queuePlaybackActions.isPlaying())
        } else {
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
        }
    }

    fun clearQueue() {
        if (queueProvider.queue().isEmpty()) {
            return
        }
        queuePlaybackActions.stopAndClear()
    }

    fun restorePlaybackQueue() {
        if (!playbackRestoreEnabled) {
            return
        }
        val restored = queueStore.load()
        if (restored.isEmpty()) {
            return
        }
        val queue = queueProvider.queue()
        queue.clear()
        for (track in restored.tracks) {
            if (!isRestorableQueueTrack(track)) {
                continue
            }
            val queueTrack = streamingRestoreProvider.restoredTrackFor(track) ?: track
            queue.add(queueTrack)
            streamingRestoreProvider.restoreForDataPath(queueTrack.dataPath)
        }
        if (queue.isEmpty()) {
            setCurrentIndex(-1)
            return
        }
        setCurrentIndex(restored.currentIndex)
        setRestoredPosition(
            queueStore.loadPlaybackPositionTrackId(),
            queueStore.loadPlaybackPositionMs(),
            false
        )
        clearErrorMessage()
        clearLastMarkedTrack()
        persistQueue()
    }

    private fun persistQueue() {
        queueStore.save(queueProvider.queue().toList(), currentIndex())
    }

    private fun persistPlaybackPosition() {
        if (playbackPositionManager != null) {
            playbackPositionManager.persistCurrentPosition(force = true)
            return
        }
        val track = currentTrack() ?: return
        queueStore.savePlaybackPosition(track.id, 0L)
    }

    private fun savePlaybackResumeRequested(requested: Boolean) {
        queueStore.saveResumeRequested(requested)
    }

    private fun playbackPositionMs(): Long {
        return playbackPositionManager?.positionMs() ?: 0L
    }

    private fun clearRestoredPosition() {
        playbackPositionManager?.clearRestoredPosition()
    }

    private fun resetCurrentPlaybackPosition() {
        playbackPositionManager?.resetCurrentPlaybackPosition()
    }

    private fun setRestoredPosition(trackId: Long, positionMs: Long, explicit: Boolean) {
        playbackPositionManager?.setRestoredPosition(trackId, positionMs, explicit)
    }

    private fun setExplicitRestoredPosition(track: Track?, positionMs: Long): Long {
        return playbackPositionManager?.setExplicitRestoredPosition(track, positionMs) ?: 0L
    }

    private fun clearErrorMessage() {
        playbackRuntimeStateManager?.setErrorMessage("")
    }

    private fun clearLastMarkedTrack() {
        playbackTransitionStateManager?.setLastMarkedTrack(null)
    }

    private fun shuffleEnabled(): Boolean {
        return playbackRuntimeStateManager?.shuffleEnabled() ?: false
    }

    private fun repeatMode(): Int {
        return playbackRuntimeStateManager?.repeatMode() ?: REPEAT_ALL
    }

    private fun preparing(): Boolean {
        return playbackRuntimeStateManager?.preparing() ?: false
    }

    private fun currentTrack(): Track? {
        val index = currentIndex()
        val queue = queueProvider.queue()
        return if (index in queue.indices) queue[index] else null
    }

    private fun isRestorableQueueTrack(track: Track): Boolean {
        if (track.id < 0L) {
            return false
        }
        if (track.dataPath.isNullOrBlank()) {
            return false
        }
        val contentUri = track.contentUri
        if (contentUri == null || Uri.EMPTY == contentUri) {
            return isStreamingPlaceholder(track)
        }
        val scheme = contentUri.scheme
        if (scheme.equals("file", ignoreCase = true)) {
            val path = contentUri.path
            return path != null && File(path).exists()
        }
        if (scheme.isNullOrBlank()) {
            return contentUri.toString().isNotBlank()
        }
        return true
    }

    private fun isStreamingPlaceholder(track: Track): Boolean {
        return track.dataPath != null && track.dataPath.startsWith("streaming:")
    }

    private object NoopQueuePlaybackActions : QueuePlaybackActions {
        override fun isPlaying(): Boolean = false
        override fun prepareCurrent(playWhenReady: Boolean) {}
        override fun publishState() {}
        override fun stopAndClear() {}
    }

    private object NoopStreamingRestoreProvider : StreamingRestoreProvider {
        override fun restoredTrackFor(track: Track): Track? = null
        override fun restoreForDataPath(dataPath: String?) {}
    }

    private object NoopMirroredQueuePlayer : MirroredQueuePlayer {
        override fun matchesCurrentQueue(): Boolean = false
        override fun seekTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean = false
    }

    private fun indexOfTrackOccurrence(queue: List<Track>, target: Track): Int {
        for (i in queue.indices) {
            val candidate = queue[i]
            if (candidate === target || candidate.id == target.id && candidate.dataPath == target.dataPath && candidate.contentUri == target.contentUri) {
                return i
            }
        }
        return -1
    }
}
