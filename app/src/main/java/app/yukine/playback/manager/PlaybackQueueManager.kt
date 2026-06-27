package app.yukine.playback.manager

import app.yukine.playback.EchoPlaybackService.REPEAT_OFF
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import java.util.HashSet
import java.util.LinkedList
import java.util.Random

internal class PlaybackQueueManager(
    private val queueStore: PlaybackQueueStore,
    private val queueProvider: QueueProvider,
    private val random: Random = Random()
) {
    private var playbackRestoreEnabled = true

    interface QueueProvider {
        fun queue(): MutableList<Track>
        fun currentIndex(): Int
        fun currentTrack(): Track?
        fun repeatMode(): Int
        fun shuffleEnabled(): Boolean
        fun isPlaying(): Boolean
        fun preparing(): Boolean
        fun clearRestoredPosition()
        fun resetCurrentPlaybackPosition()
        fun savePlaybackResumeRequested(requested: Boolean)
        fun prepareCurrent(playWhenReady: Boolean)
        fun publishState()
        fun stopAndClear()
        fun seekMirroredQueueToCurrentIndex(playWhenReady: Boolean): Boolean
        fun playbackPositionMs(): Long
        fun restoredTrackFor(track: Track): Track?
        fun restoreForDataPath(dataPath: String?)
        fun isRestorableQueueTrack(track: Track): Boolean
        fun setRestoredPosition(trackId: Long, positionMs: Long, explicit: Boolean)
        fun setCurrentIndex(index: Int)
        fun setErrorMessage(message: String)
        fun setPreparing(preparing: Boolean)
        fun setLastMarkedTrack(track: Track?)
        fun queueIsEmpty(): Boolean
        fun samePlaybackUri(first: Track?, second: Track?): Boolean
        fun setExplicitRestoredPosition(track: Track?, positionMs: Long): Long
        fun recordStreamingRecovery(track: Track, restoredPositionMs: Long)
        fun schedulePrepareCurrent(playWhenReady: Boolean)
        fun mirroredQueueMatchesCurrentPlayer(): Boolean
        fun resetWaveformIfTrackChanged(track: Track)
        fun applyPlaybackParametersToPlayer()
        fun applyPlaybackModeToPlayer()
        fun seekMirroredQueueTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean
        fun setPlayerMirrorsQueue(enabled: Boolean)
        fun acquireWifiLockIfStreaming()
        fun startProgressUpdates()
    }

    constructor(queueStore: PlaybackQueueStore, queueProvider: QueueProvider) : this(queueStore, queueProvider, Random())

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        playbackRestoreEnabled = enabled
    }

    fun loadRestoredQueue(): PlaybackQueueState {
        return queueStore.load()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = queueProvider.queue()
        queue.clear()
        queue.addAll(tracks)
        queueProvider.setCurrentIndex(maxOf(0, minOf(startIndex, queue.size - 1)))
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        queueProvider.resetCurrentPlaybackPosition()
        if (startPositionMs >= 0L) {
            queueProvider.currentTrack()?.let { track ->
                queueProvider.setRestoredPosition(track.id, startPositionMs, explicit = true)
            }
        }
        queueProvider.savePlaybackResumeRequested(true)
        queueProvider.prepareCurrent(true)
    }

    fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = queueProvider.queue()
        val wasEmpty = queue.isEmpty()
        queue.addAll(tracks)
        if (queueProvider.currentIndex() < 0 || queueProvider.currentIndex() >= queue.size) {
            queueProvider.setCurrentIndex(0)
        }
        persistQueue()
        if (wasEmpty) {
            queueProvider.setErrorMessage("")
            queueProvider.setLastMarkedTrack(null)
            queueProvider.clearRestoredPosition()
            queueProvider.resetCurrentPlaybackPosition()
            queueProvider.savePlaybackResumeRequested(true)
            queueProvider.prepareCurrent(true)
            return
        }
        queueProvider.publishState()
    }

    fun playFirstQueuedTrack() {
        if (queueProvider.queueIsEmpty()) {
            return
        }
        queueProvider.setCurrentIndex(0)
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        queueProvider.resetCurrentPlaybackPosition()
        queueProvider.prepareCurrent(true)
    }

    fun advanceQueueIndexToNext() {
        val queue = queueProvider.queue()
        if (queue.isEmpty()) {
            return
        }
        if (queueProvider.shuffleEnabled() && queue.size > 1) {
            var nextIndex = queueProvider.currentIndex()
            while (nextIndex == queueProvider.currentIndex()) {
                nextIndex = random.nextInt(queue.size)
            }
            queueProvider.setCurrentIndex(nextIndex)
            return
        }
        if (queueProvider.currentIndex() >= queue.size - 1) {
            if (queueProvider.repeatMode() == REPEAT_OFF) {
                persistQueue()
                queueProvider.publishState()
                return
            }
            queueProvider.setCurrentIndex(0)
            return
        }
        queueProvider.setCurrentIndex(queueProvider.currentIndex() + 1)
    }

    fun skipToNextImmediately() {
        persistPlaybackPosition()
        advanceQueueIndexToNext()
        if (queueProvider.seekMirroredQueueToCurrentIndex(true)) {
            return
        }
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        queueProvider.resetCurrentPlaybackPosition()
        queueProvider.savePlaybackResumeRequested(true)
        queueProvider.prepareCurrent(true)
    }

    fun skipToPrevious() {
        val queue = queueProvider.queue()
        if (queue.isEmpty()) {
            return
        }
        persistPlaybackPosition()
        queueProvider.setCurrentIndex(
            if (queueProvider.currentIndex() <= 0) queue.size - 1 else queueProvider.currentIndex() - 1
        )
        if (queueProvider.seekMirroredQueueToCurrentIndex(true)) {
            return
        }
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        queueProvider.resetCurrentPlaybackPosition()
        queueProvider.savePlaybackResumeRequested(true)
        queueProvider.prepareCurrent(true)
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        val queue = queueProvider.queue()
        if (queue.isEmpty() || fromIndex == toIndex || fromIndex < 0 || fromIndex >= queue.size) {
            return
        }
        val targetIndex = maxOf(0, minOf(toIndex, queue.size - 1))
        val current = queueProvider.currentTrack()
        val moved = queue.removeAt(fromIndex)
        queue.add(targetIndex, moved)
        if (current != null) {
            queueProvider.setCurrentIndex(indexOfTrackOccurrence(queue, current))
        } else {
            queueProvider.setCurrentIndex(maxOf(0, minOf(queueProvider.currentIndex(), queue.size - 1)))
        }
        persistQueue()
        persistPlaybackPosition()
        queueProvider.publishState()
    }

    fun removeTracksById(trackIds: Set<Long>) {
        val queue = queueProvider.queue()
        if (trackIds.isEmpty() || queue.isEmpty()) {
            return
        }
        val current = queueProvider.currentTrack()
        val removedCurrent = current != null && trackIds.contains(current.id)
        var removedBeforeCurrent = 0
        for (i in queue.indices) {
            if (i < queueProvider.currentIndex() && trackIds.contains(queue[i].id)) {
                removedBeforeCurrent++
            }
        }
        for (i in queue.size - 1 downTo 0) {
            if (trackIds.contains(queue[i].id)) {
                queue.removeAt(i)
            }
        }
        if (queue.isEmpty()) {
            queueProvider.stopAndClear()
            return
        }
        if (queueProvider.currentIndex() >= 0) {
            queueProvider.setCurrentIndex(maxOf(0, queueProvider.currentIndex() - removedBeforeCurrent))
        }
        if (queueProvider.currentIndex() >= queue.size) {
            queueProvider.setCurrentIndex(queue.size - 1)
        }
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        if (removedCurrent) {
            queueProvider.resetCurrentPlaybackPosition()
            queueProvider.prepareCurrent(false)
        } else {
            persistPlaybackPosition()
            queueProvider.publishState()
        }
    }

    fun retainTracksById(trackIdsToKeep: Set<Long>) {
        if (trackIdsToKeep.isEmpty() || queueProvider.queueIsEmpty()) {
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
                if (i == queueProvider.currentIndex()) {
                    replacedCurrent = true
                }
                queue[i] = replacement
                replaced = true
            }
        }
        if (replaced) {
            queueProvider.setErrorMessage("")
            queueProvider.setLastMarkedTrack(null)
            queueProvider.clearRestoredPosition()
            persistQueue()
            if (replacedCurrent) {
                queueProvider.resetCurrentPlaybackPosition()
                queueProvider.prepareCurrent(queueProvider.isPlaying() || queueProvider.preparing() || queueProvider.queueIsEmpty())
                return
            }
            queueProvider.publishState()
        }
    }

    fun replaceQueuedTrackById(oldTrackId: Long, replacement: Track) {
        if (queueProvider.queueIsEmpty()) {
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
                if (i == queueProvider.currentIndex()) {
                    replacedCurrent = true
                }
            }
        }
        if (!replaced) {
            replaceQueuedTrack(replacement)
            return
        }
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        if (replacedCurrent) {
            queueProvider.resetCurrentPlaybackPosition()
            queueProvider.prepareCurrent(queueProvider.isPlaying())
        } else {
            persistPlaybackPosition()
            queueProvider.publishState()
        }
    }

    fun replaceCurrentTrackAndResume(replacement: Track?, positionMs: Long) {
        val queue = queueProvider.queue()
        if (replacement == null || queueProvider.currentIndex() < 0 || queueProvider.currentIndex() >= queue.size) {
            return
        }
        val current = queueProvider.currentTrack()
        val resumePositionMs = maxOf(maxOf(0L, positionMs), queueProvider.playbackPositionMs())
        queue[queueProvider.currentIndex()] = replacement
        queueProvider.setErrorMessage("")
        if (queueProvider.samePlaybackUri(current, replacement)) {
            persistQueue()
            persistPlaybackPosition()
            queueProvider.publishState()
            return
        }
        queueProvider.setLastMarkedTrack(null)
        val restoredPositionMs = queueProvider.setExplicitRestoredPosition(replacement, resumePositionMs)
        queueProvider.recordStreamingRecovery(replacement, restoredPositionMs)
        persistQueue()
        queueProvider.schedulePrepareCurrent(true)
    }

    fun reuseMirroredQueueIfAvailable(playWhenReady: Boolean, startPositionMs: Long): Boolean {
        val queue = queueProvider.queue()
        if (queue.isEmpty() || !queueProvider.mirroredQueueMatchesCurrentPlayer()) {
            return false
        }
        val targetIndex = maxOf(0, minOf(queueProvider.currentIndex(), queue.size - 1))
        val track = queueProvider.currentTrack() ?: return false
        queueProvider.setPreparing(false)
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.resetWaveformIfTrackChanged(track)
        queueProvider.restoreForDataPath(track.dataPath)
        queueProvider.applyPlaybackParametersToPlayer()
        queueProvider.applyPlaybackModeToPlayer()
        val startAtMs = maxOf(0L, startPositionMs)
        if (!queueProvider.seekMirroredQueueTo(targetIndex, startAtMs, playWhenReady)) {
            queueProvider.setPlayerMirrorsQueue(false)
            return false
        }
        if (playWhenReady) {
            queueProvider.savePlaybackResumeRequested(true)
            queueProvider.acquireWifiLockIfStreaming()
        }
        if (startPositionMs > 0L) {
            queueProvider.clearRestoredPosition()
        }
        queueProvider.publishState()
        queueProvider.startProgressUpdates()
        return true
    }

    private fun replaceAndCollapseQueuedTrack(oldTrackId: Long, replacement: Track) {
        val queue = queueProvider.queue()
        var preferredIndex = -1
        if (queueProvider.currentIndex() >= 0 && queueProvider.currentIndex() < queue.size) {
            val currentId = queue[queueProvider.currentIndex()].id
            if (currentId == oldTrackId || currentId == replacement.id) {
                preferredIndex = queueProvider.currentIndex()
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
                if (i == queueProvider.currentIndex()) {
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
            if (i == queueProvider.currentIndex()) {
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
            queueProvider.stopAndClear()
            return
        }
        if (collapsedCurrentIndex >= 0) {
            queueProvider.setCurrentIndex(collapsedCurrentIndex)
        } else if (queueProvider.currentIndex() >= 0) {
            queueProvider.setCurrentIndex(minOf(queueProvider.currentIndex(), queue.size - 1))
        } else {
            queueProvider.setCurrentIndex(-1)
        }
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        queueProvider.clearRestoredPosition()
        persistQueue()
        if (currentWasOldTrack) {
            queueProvider.resetCurrentPlaybackPosition()
            queueProvider.prepareCurrent(queueProvider.isPlaying())
        } else {
            persistPlaybackPosition()
            queueProvider.publishState()
        }
    }

    fun clearQueue() {
        if (queueProvider.queueIsEmpty()) {
            return
        }
        queueProvider.stopAndClear()
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
            if (!queueProvider.isRestorableQueueTrack(track)) {
                continue
            }
            val queueTrack = queueProvider.restoredTrackFor(track) ?: track
            queue.add(queueTrack)
            queueProvider.restoreForDataPath(queueTrack.dataPath)
        }
        if (queue.isEmpty()) {
            queueProvider.setCurrentIndex(-1)
            return
        }
        queueProvider.setCurrentIndex(restored.currentIndex)
        queueProvider.setRestoredPosition(
            queueStore.loadPlaybackPositionTrackId(),
            queueStore.loadPlaybackPositionMs(),
            false
        )
        queueProvider.setErrorMessage("")
        queueProvider.setLastMarkedTrack(null)
        persistQueue()
    }

    private fun persistQueue() {
        queueStore.save(queueProvider.queue().toList(), queueProvider.currentIndex())
    }

    private fun persistPlaybackPosition() {
        val track = queueProvider.currentTrack() ?: return
        queueStore.savePlaybackPosition(track.id, queueProvider.playbackPositionMs())
    }

    private fun indexOfTrackOccurrence(queue: List<Track>, target: Track): Int {
        if (target == null) {
            return -1
        }
        for (i in queue.indices) {
            val candidate = queue[i]
            if (candidate === target || candidate.id == target.id && candidate.dataPath == target.dataPath && candidate.contentUri == target.contentUri) {
                return i
            }
        }
        return -1
    }
}
