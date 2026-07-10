package app.yukine.playback.manager

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL
import app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF
import app.yukine.playback.PlaybackRepeatMode.REPEAT_ONE
import app.yukine.model.Track
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.LinkedList
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(UnstableApi::class)
internal class PlaybackQueueManager(
    private val queueStore: PlaybackQueueStore,
    private val queue: MutableList<Track>,
    private val queuePlaybackActions: QueuePlaybackActions,
    private val playbackPositionManager: PlaybackPositionManager? = null,
    private val streamingRestoreProvider: StreamingRestoreProvider,
    private val mirroredQueuePlayer: MirroredQueuePlayer,
    private val playbackRuntimeStateManager: PlaybackRuntimeStateManager? = null,
    private val playbackTransitionStateManager: PlaybackTransitionStateManager? = null,
    private val random: Random = Random()
) {
    private companion object {
        const val MAX_MIRRORED_QUEUE_TRACKS = 64
        const val MAX_SYNC_PERSISTED_QUEUE_TRACKS = 64
    }

    private var playbackRestoreEnabled = queueStore.loadPlaybackRestoreEnabled()
    private var currentIndex = -1
    private var queueRevision = 0L

    interface QueuePlaybackActions {
        fun prepareCurrent(playWhenReady: Boolean)
        fun publishState()
        fun stopAndClear()
        fun persistQueueAsync(tracks: List<Track>, currentIndex: Int): Boolean
    }

    interface StreamingRestoreProvider {
        fun restoredTrackFor(track: Track): Track?
        fun restoreForDataPath(dataPath: String?)
    }

    interface MirroredQueuePlayer {
        fun matchesCurrentQueue(): Boolean
        fun seekTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean
    }

    interface QueueTrackMatcher {
        fun matches(index: Int, track: Track): Boolean
    }

    data class CurrentTrackReplacementRecovery(
        val track: Track,
        val restoredPositionMs: Long,
        val playWhenReady: Boolean
    )

    data class MirroredTransitionResult(
        val completedIndex: Int,
        val stopAfterAutomaticAdvance: Boolean
    )

    data class QueuePreparation(
        val currentTrack: Track?,
        val startIndex: Int,
        val mirroredQueueTracks: List<Track>?
    ) {
        companion object {
            @JvmStatic
            fun empty(): QueuePreparation = QueuePreparation(
                currentTrack = null,
                startIndex = 0,
                mirroredQueueTracks = null
            )
        }
    }

    data class QueueStateSnapshot(
        val currentTrack: Track?,
        val currentIndex: Int,
        val queueSize: Int,
        val queueRevision: Long
    ) {
        constructor(
            currentTrack: Track?,
            currentIndex: Int,
            queueSize: Int
        ) : this(currentTrack, currentIndex, queueSize, 0L)

        val isQueueEmpty: Boolean
            get() = queueSize <= 0

        val hasCurrentTrack: Boolean
            get() = currentTrack != null

        val hasMultipleTracks: Boolean
            get() = queueSize >= 2

        val isAtEndOfQueue: Boolean
            get() = currentIndex >= queueSize - 1

        companion object {
            @JvmStatic
            fun empty(): QueueStateSnapshot = QueueStateSnapshot(
                currentTrack = null,
                currentIndex = -1,
                queueSize = 0,
                queueRevision = 0L
            )
        }
    }

    data class RestorePlaybackResult(
        val shouldCreatePlayer: Boolean,
        val shouldPrepare: Boolean,
        val playWhenReady: Boolean
    ) {
        companion object {
            @JvmStatic
            fun empty(): RestorePlaybackResult = RestorePlaybackResult(
                shouldCreatePlayer = false,
                shouldPrepare = false,
                playWhenReady = false
            )
        }
    }

    enum class PlaybackCompletionAction {
        STOP_AND_CLEAR,
        REPEAT_CURRENT,
        STOP_AT_END,
        ADVANCE_TO_NEXT
    }

    constructor(
        queueStore: PlaybackQueueStore,
        queuePlaybackActions: QueuePlaybackActions,
        playbackPositionManager: PlaybackPositionManager?,
        streamingRestoreProvider: StreamingRestoreProvider,
        mirroredQueuePlayer: MirroredQueuePlayer,
        playbackRuntimeStateManager: PlaybackRuntimeStateManager?,
        playbackTransitionStateManager: PlaybackTransitionStateManager?
    ) : this(
        queueStore,
        CopyOnWriteArrayList(),
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
        queueStore.savePlaybackRestoreEnabled(enabled)
    }

    private fun currentIndex(): Int {
        return currentIndex
    }

    private fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    private fun clampCurrentIndex(): Int {
        val queueSize = queue.size
        if (queueSize <= 0) {
            return 0
        }
        return maxOf(0, minOf(currentIndex, queueSize - 1))
    }

    private fun setClampedCurrentIndex(index: Int) {
        val queueSize = queue.size
        currentIndex = if (queueSize <= 0) {
            -1
        } else {
            maxOf(0, minOf(index, queueSize - 1))
        }
    }

    fun applyMirroredTransitionIndex(nextIndex: Int, automaticAdvance: Boolean): MirroredTransitionResult? {
        val queue = this.queue
        if (nextIndex < 0 || nextIndex >= queue.size || nextIndex == currentIndex()) {
            return null
        }
        val completedIndex = currentIndex()
        if (automaticAdvance && repeatMode() == REPEAT_OFF) {
            return MirroredTransitionResult(completedIndex, stopAfterAutomaticAdvance = true)
        }
        setCurrentIndex(nextIndex)
        return MirroredTransitionResult(completedIndex, stopAfterAutomaticAdvance = false)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = this.queue
        queue.clear()
        queue.addAll(tracks)
        markQueueContentChanged()
        setCurrentIndex(maxOf(0, minOf(startIndex, queue.size - 1)))
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueueAroundPlaybackAction {
            resetCurrentPlaybackPosition()
            if (startPositionMs >= 0L) {
                currentTrack()?.let { track ->
                    setRestoredPosition(track.id, startPositionMs, explicit = true)
                }
            }
            savePlaybackResumeRequested(true)
            queuePlaybackActions.prepareCurrent(true)
        }
    }

    fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        val queue = this.queue
        val wasEmpty = queue.isEmpty()
        queue.addAll(tracks)
        markQueueContentChanged()
        if (currentIndex() < 0 || currentIndex() >= queue.size) {
            setCurrentIndex(0)
        }
        if (wasEmpty) {
            clearErrorMessage()
            clearLastMarkedTrack()
            clearRestoredPosition()
            persistQueueAroundPlaybackAction {
                resetCurrentPlaybackPosition()
                savePlaybackResumeRequested(true)
                queuePlaybackActions.prepareCurrent(true)
            }
            return
        }
        persistQueue()
        queuePlaybackActions.publishState()
    }

    fun playFirstQueuedTrack() {
        if (queue.isEmpty()) {
            return
        }
        setCurrentIndex(0)
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueueAroundPlaybackAction {
            resetCurrentPlaybackPosition()
            savePlaybackResumeRequested(true)
            queuePlaybackActions.prepareCurrent(true)
        }
    }

    /**
     * Prepares an explicit user play request. An active service recovery starts from the
     * beginning, while a user-paused checkpoint remains available for resuming.
     */
    fun prepareCurrentForExplicitPlay(): Boolean {
        if (currentTrack() == null) {
            return false
        }
        if (queueStore.loadResumeRequested()) {
            clearRestoredPosition()
            resetCurrentPlaybackPosition()
        }
        savePlaybackResumeRequested(true)
        queuePlaybackActions.prepareCurrent(true)
        return true
    }

    private fun advanceQueueIndexToNext(): Boolean {
        val queue = this.queue
        if (queue.isEmpty()) {
            return false
        }
        if (shuffleEnabled() && queue.size > 1) {
            var nextIndex = currentIndex()
            while (nextIndex == currentIndex()) {
                nextIndex = random.nextInt(queue.size)
            }
            setCurrentIndex(nextIndex)
            return true
        }
        if (currentIndex() >= queue.size - 1) {
            if (repeatMode() == REPEAT_OFF) {
                persistQueue()
                queuePlaybackActions.publishState()
                return false
            }
            setCurrentIndex(0)
            return true
        }
        setCurrentIndex(currentIndex() + 1)
        return true
    }

    fun skipToNextImmediately(): Boolean {
        if (queue.isEmpty()) {
            return false
        }
        persistPlaybackPosition()
        if (!advanceQueueIndexToNext()) {
            return false
        }
        if (reuseMirroredQueueAtCurrentIndex(true)) {
            return true
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueueAroundPlaybackAction {
            resetCurrentPlaybackPosition()
            savePlaybackResumeRequested(true)
            queuePlaybackActions.prepareCurrent(true)
        }
        return false
    }

    fun playbackCompletionAction(): PlaybackCompletionAction {
        if (queue.isEmpty()) {
            return PlaybackCompletionAction.STOP_AND_CLEAR
        }
        if (repeatMode() == REPEAT_ONE) {
            return PlaybackCompletionAction.REPEAT_CURRENT
        }
        if (repeatMode() == REPEAT_OFF && isAtEndOfQueue()) {
            return PlaybackCompletionAction.STOP_AT_END
        }
        return PlaybackCompletionAction.ADVANCE_TO_NEXT
    }

    fun preparePlaybackCompletion(action: PlaybackCompletionAction) {
        if (action == PlaybackCompletionAction.STOP_AND_CLEAR) {
            return
        }
        saveCurrentTrackPlaybackPosition(0L)
        if (action == PlaybackCompletionAction.REPEAT_CURRENT) {
            clearRestoredPosition()
        }
    }

    fun prepareStopAtEndOfQueue() {
        clearPlaybackPosition()
        playbackRuntimeStateManager?.setPreparing(false)
        clearErrorMessage()
        clearLastMarkedTrack()
        savePlaybackResumeRequested(false)
    }

    fun prepareStopAfterAutomaticAdvance(completedIndex: Int) {
        persistPlaybackPosition()
        setClampedCurrentIndex(completedIndex)
        resetCurrentPlaybackPosition()
    }

    fun prepareMirroredTransitionPlaybackState() {
        persistPlaybackPosition()
        clearErrorMessage()
        clearLastMarkedTrack()
        currentTrack()?.let { track ->
            streamingRestoreProvider.restoreForDataPath(track.dataPath)
        }
        clearRestoredPosition()
        resetCurrentPlaybackPosition()
        persistQueue()
    }

    fun prepareStopAndClearPlaybackState() {
        clearPlaybackPosition()
        playbackRuntimeStateManager?.setPreparing(false)
        clearErrorMessage()
        playbackTransitionStateManager?.clear()
        clearQueueState()
        persistQueue()
        savePlaybackResumeRequested(false)
    }

    fun consumeRestoredPositionAfterPrepare(startPositionMs: Long) {
        if (startPositionMs > 0L) {
            clearRestoredPosition()
        }
    }

    fun restoredPositionFor(track: Track?): Long {
        return playbackPositionManager?.restoredPositionFor(track) ?: 0L
    }

    fun persistCurrentPlaybackPosition(force: Boolean) {
        playbackPositionManager?.persistCurrentPosition(force)
    }

    fun persistPausedPlaybackPosition() {
        playbackPositionManager?.persistCurrentPositionForPause()
    }

    /** Drops the checkpoint that is valid only until paused playback actually resumes. */
    fun clearPausedPlaybackPosition() {
        clearPlaybackPosition()
    }

    fun skipToPrevious(): Boolean {
        val queue = this.queue
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
        persistQueueAroundPlaybackAction {
            resetCurrentPlaybackPosition()
            savePlaybackResumeRequested(true)
            queuePlaybackActions.prepareCurrent(true)
        }
        return false
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        val queue = this.queue
        if (queue.isEmpty() || fromIndex == toIndex || fromIndex < 0 || fromIndex >= queue.size) {
            return
        }
        val targetIndex = maxOf(0, minOf(toIndex, queue.size - 1))
        val current = currentTrack()
        val moved = queue.removeAt(fromIndex)
        queue.add(targetIndex, moved)
        markQueueContentChanged()
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
        val queue = this.queue
        if (trackIds.isEmpty() || queue.isEmpty()) {
            return
        }
        val current = currentTrack()
        val removedCurrent = current != null && trackIds.contains(current.id)
        var removedBeforeCurrent = 0
        var removedAny = false
        val retainedTracks = ArrayList<Track>(queue.size)
        for ((index, track) in queue.withIndex()) {
            if (trackIds.contains(track.id)) {
                removedAny = true
                if (index < currentIndex()) {
                    removedBeforeCurrent++
                }
            } else {
                retainedTracks.add(track)
            }
        }
        if (!removedAny) {
            return
        }
        // The production queue intentionally uses CopyOnWriteArrayList. Commit a bulk removal
        // once rather than copying its backing array once per removed track.
        queue.clear()
        queue.addAll(retainedTracks)
        markQueueContentChanged()
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
        if (queue.isEmpty()) {
            return
        }
        if (trackIdsToKeep.isEmpty()) {
            clearQueue()
            return
        }
        val trackIdsToRemove = HashSet<Long>()
        for (track in queue) {
            if (!trackIdsToKeep.contains(track.id)) {
                trackIdsToRemove.add(track.id)
            }
        }
        removeTracksById(trackIdsToRemove)
    }

    fun replaceQueuedTrack(replacement: Track) {
        val queue = this.queue
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
            markQueueContentChanged()
            clearErrorMessage()
            clearLastMarkedTrack()
            clearRestoredPosition()
            persistQueue()
            if (replacedCurrent) {
                resetCurrentPlaybackPosition()
                queuePlaybackActions.prepareCurrent(isPlaying() || preparing() || queue.isEmpty())
                return
            }
            queuePlaybackActions.publishState()
        }
    }

    fun replaceQueuedTrackById(oldTrackId: Long, replacement: Track) {
        if (queue.isEmpty()) {
            return
        }
        if (oldTrackId == replacement.id) {
            replaceQueuedTrack(replacement)
            return
        }
        val queue = this.queue
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
        markQueueContentChanged()
        clearErrorMessage()
        clearLastMarkedTrack()
        clearRestoredPosition()
        persistQueue()
        if (replacedCurrent) {
            resetCurrentPlaybackPosition()
            queuePlaybackActions.prepareCurrent(isPlaying())
        } else {
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
        }
    }

    fun replaceCurrentTrackAndResume(replacement: Track?, positionMs: Long): CurrentTrackReplacementRecovery? {
        val queue = this.queue
        if (replacement == null || currentIndex() < 0 || currentIndex() >= queue.size) {
            return null
        }
        val current = currentTrack()
        // This path refreshes the source for the current logical song. A delayed streaming
        // recovery can arrive after the user has selected another song, so it must never
        // replace that song or transfer the old playback position across track IDs.
        if (current == null || current.id != replacement.id) {
            return null
        }
        return replaceCurrentTrackAtCurrentIndex(current, replacement, positionMs)
    }

    /**
     * Replaces the active queue item with another source for the same logical song.
     *
     * Unlike streaming URL refreshes, alternate sources can have different physical track IDs.
     * The expected current ID keeps a late resolution from replacing a song the user has already
     * changed to, while still allowing a deliberate source switch to retain its current position.
     */
    fun replaceCurrentSourceAndResume(
        expectedTrackId: Long,
        replacement: Track?,
        positionMs: Long
    ): CurrentTrackReplacementRecovery? {
        val queue = this.queue
        if (replacement == null || currentIndex() < 0 || currentIndex() >= queue.size) {
            return null
        }
        val current = currentTrack()
        if (current == null || current.id != expectedTrackId) {
            return null
        }
        return replaceCurrentTrackAtCurrentIndex(current, replacement, positionMs)
    }

    private fun replaceCurrentTrackAtCurrentIndex(
        current: Track,
        replacement: Track,
        positionMs: Long
    ): CurrentTrackReplacementRecovery? {
        val queue = this.queue
        val resumePositionMs = maxOf(maxOf(0L, positionMs), playbackPositionMs())
        queue[currentIndex()] = replacement
        markQueueContentChanged()
        clearErrorMessage()
        if (current.contentUri == replacement.contentUri && current.dataPath == replacement.dataPath) {
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

    fun replaceCurrentQueueTrack(replacement: Track?): Boolean {
        val queue = this.queue
        if (replacement == null || currentIndex() < 0 || currentIndex() >= queue.size) {
            return false
        }
        queue[currentIndex()] = replacement
        markQueueContentChanged()
        persistQueue()
        return true
    }

    fun reuseMirroredQueueIfAvailable(playWhenReady: Boolean, startPositionMs: Long): Boolean {
        return reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs, resetCurrentPlaybackState = false)
    }

    private fun reuseMirroredQueueAtCurrentIndex(playWhenReady: Boolean): Boolean {
        return reuseMirroredQueueIfAvailable(playWhenReady, 0L, resetCurrentPlaybackState = true)
    }

    private fun reuseMirroredQueueIfAvailable(
        playWhenReady: Boolean,
        startPositionMs: Long,
        resetCurrentPlaybackState: Boolean
    ): Boolean {
        val queue = this.queue
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

    private fun mirroredQueueTracksForPreparation(): List<Track>? {
        val queue = this.queue
        if (queue.isEmpty() || currentTrack() == null) {
            return null
        }
        if (queue.size > MAX_MIRRORED_QUEUE_TRACKS) {
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

    fun queuePreparationForNewPlayer(): QueuePreparation {
        val track = currentTrack() ?: return QueuePreparation.empty()
        return QueuePreparation(
            currentTrack = track,
            startIndex = safeCurrentIndex(),
            mirroredQueueTracks = mirroredQueueTracksForPreparation()
        )
    }

    private fun replaceAndCollapseQueuedTrack(oldTrackId: Long, replacement: Track) {
        val queue = this.queue
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
        markQueueContentChanged()
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
            queuePlaybackActions.prepareCurrent(isPlaying())
        } else {
            persistPlaybackPosition()
            queuePlaybackActions.publishState()
        }
    }

    fun clearQueue() {
        if (queue.isEmpty()) {
            return
        }
        queuePlaybackActions.stopAndClear()
    }

    private fun clearQueueState() {
        val hadQueueContent = queue.isNotEmpty() || currentIndex() >= 0
        queue.clear()
        setCurrentIndex(-1)
        if (hadQueueContent) {
            markQueueContentChanged()
        }
    }

    fun persistQueueState() {
        persistQueue()
    }

    fun restorePlaybackQueue(): QueueStateSnapshot {
        if (!playbackRestoreEnabled) {
            clearPlaybackPosition()
            return queueStateSnapshot()
        }
        val resumeRequested = queueStore.loadResumeRequested()
        val restored = queueStore.load()
        if (restored.isEmpty()) {
            clearPlaybackPosition()
            savePlaybackResumeRequested(false)
            return queueStateSnapshot()
        }
        val queue = this.queue
        val restoredTracks = ArrayList<Track>(restored.tracks.size)
        var restoredCurrentIndex = -1
        var fallbackBeforeRestoredIndex = -1
        var fallbackAfterRestoredIndex = -1
        for ((storedIndex, track) in restored.tracks.withIndex()) {
            if (!PlaybackMediaSourceProvider.isRestorableQueueTrack(track)) {
                continue
            }
            restoredTracks.add(track)
            val restoredQueueIndex = restoredTracks.lastIndex
            when {
                storedIndex == restored.currentIndex -> restoredCurrentIndex = restoredQueueIndex
                storedIndex < restored.currentIndex -> fallbackBeforeRestoredIndex = restoredQueueIndex
                fallbackAfterRestoredIndex < 0 -> fallbackAfterRestoredIndex = restoredQueueIndex
            }
        }
        if (restoredTracks.isEmpty()) {
            val hadQueueContent = queue.isNotEmpty() || currentIndex() >= 0
            queue.clear()
            if (hadQueueContent) {
                markQueueContentChanged()
            }
            setCurrentIndex(-1)
            clearPlaybackPosition()
            persistQueue()
            savePlaybackResumeRequested(false)
            return queueStateSnapshot()
        }
        queue.clear()
        queue.addAll(restoredTracks)
        markQueueContentChanged()
        when {
            restored.currentIndex < 0 -> setCurrentIndex(-1)
            restoredCurrentIndex >= 0 -> setCurrentIndex(restoredCurrentIndex)
            fallbackAfterRestoredIndex >= 0 -> setCurrentIndex(fallbackAfterRestoredIndex)
            fallbackBeforeRestoredIndex >= 0 -> setCurrentIndex(fallbackBeforeRestoredIndex)
            else -> setClampedCurrentIndex(restored.currentIndex)
        }
        if (restoreStreamingQueueTracksAfterSelection()) {
            markQueueContentChanged()
        }
        val checkpointTrackId = queueStore.loadPlaybackPositionTrackId()
        val checkpointPositionMs = queueStore.loadPlaybackPositionMs()
        val checkpointTrack = currentTrack()
        if (!resumeRequested &&
            checkpointTrack != null &&
            checkpointTrack.id == checkpointTrackId &&
            checkpointPositionMs > 0L
        ) {
            // A user pause is the only persisted checkpoint. It is explicit so streaming
            // tracks can use the same restored position after a cold service recreation.
            setRestoredPosition(
                checkpointTrackId,
                checkpointPositionMs,
                explicit = true
            )
        } else {
            // Active shutdowns, invalid tracks, and zero/legacy records always restart at 0.
            clearPlaybackPosition()
        }
        clearErrorMessage()
        clearLastMarkedTrack()
        persistQueue()
        return queueStateSnapshot()
    }

    private fun restoreStreamingQueueTracksAfterSelection(): Boolean {
        var restoredAny = false
        if (queue.size <= MAX_MIRRORED_QUEUE_TRACKS) {
            for (index in queue.indices) {
                restoredAny = restoreStreamingQueueTrackAt(index) || restoredAny
            }
            return restoredAny
        }
        val index = currentIndex()
        if (index in queue.indices) {
            restoredAny = restoreStreamingQueueTrackAt(index)
        }
        return restoredAny
    }

    private fun restoreStreamingQueueTrackAt(index: Int): Boolean {
        val track = queue.getOrNull(index) ?: return false
        val restoredTrack = streamingRestoreProvider.restoredTrackFor(track) ?: track
        if (restoredTrack !== track) {
            queue[index] = restoredTrack
        }
        streamingRestoreProvider.restoreForDataPath(restoredTrack.dataPath)
        return restoredTrack !== track
    }

    fun restoreLastPlayback(playWhenRestored: Boolean): RestorePlaybackResult {
        val snapshot = restorePlaybackQueue()
        if (snapshot.isQueueEmpty) {
            return RestorePlaybackResult.empty()
        }
        if (!snapshot.hasCurrentTrack) {
            return RestorePlaybackResult(
                shouldCreatePlayer = true,
                shouldPrepare = false,
                playWhenReady = false
            )
        }
        val playWhenReady = playWhenRestored || queueStore.loadResumeRequested()
        if (playWhenReady) {
            savePlaybackResumeRequested(true)
        }
        return RestorePlaybackResult(
            shouldCreatePlayer = true,
            shouldPrepare = true,
            playWhenReady = playWhenReady
        )
    }

    private fun persistQueue() {
        val tracks: List<Track> = queue
        val index = currentIndex()
        if (tracks.size > MAX_SYNC_PERSISTED_QUEUE_TRACKS &&
            queuePlaybackActions.persistQueueAsync(tracks, index)
        ) {
            return
        }
        queueStore.save(tracks.toList(), index)
    }

    private inline fun persistQueueAroundPlaybackAction(action: () -> Unit) {
        if (queue.size > MAX_SYNC_PERSISTED_QUEUE_TRACKS) {
            action()
            persistQueue()
            return
        }
        persistQueue()
        action()
    }

    private fun persistPlaybackPosition() {
        persistCurrentPlaybackPosition(force = true)
    }

    private fun saveCurrentTrackPlaybackPosition(positionMs: Long) {
        playbackPositionManager?.saveTrackPosition(currentTrack(), positionMs)
    }

    fun savePlaybackResumeRequested(requested: Boolean) {
        queueStore.saveResumeRequested(requested)
    }

    private fun playbackPositionMs(): Long {
        return playbackPositionManager?.positionMs() ?: 0L
    }

    private fun clearRestoredPosition() {
        playbackPositionManager?.clearRestoredPosition()
    }

    private fun clearPlaybackPosition() {
        if (playbackPositionManager != null) {
            playbackPositionManager.clearPlaybackPosition()
            return
        }
        queueStore.savePlaybackPosition(-1L, 0L)
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

    private fun isPlaying(): Boolean {
        return playbackRuntimeStateManager?.isPlaying() ?: false
    }

    private fun currentTrack(): Track? {
        val index = currentIndex()
        val queue = this.queue
        return if (index in queue.indices) queue[index] else null
    }

    fun queueSnapshot(): List<Track> {
        return Collections.unmodifiableList(ArrayList(queue))
    }

    fun queueSize(): Int {
        return queue.size
    }

    fun trackAt(index: Int): Track? {
        val queue = this.queue
        return if (index in queue.indices) queue[index] else null
    }

    fun queueStateSnapshot(): QueueStateSnapshot {
        val queue = this.queue
        val index = currentIndex()
        val currentTrack = if (index in queue.indices) queue[index] else null
        return QueueStateSnapshot(
            currentTrack = currentTrack,
            currentIndex = index,
            queueSize = queue.size,
            queueRevision = queueRevision
        )
    }

    fun upcomingTracksForPrecache(maxCount: Int): List<Track> {
        val queue = this.queue
        val currentIndex = currentIndex()
        if (queue.isEmpty() || currentIndex < 0 || maxCount <= 0) {
            return emptyList()
        }
        val count = minOf(queue.size, maxCount)
        val tracks = ArrayList<Track>()
        for (offset in 1..count) {
            val rawIndex = currentIndex + offset
            if (rawIndex >= queue.size && repeatMode() == REPEAT_OFF) {
                break
            }
            val index = rawIndex % queue.size
            if (index == currentIndex) {
                continue
            }
            tracks.add(queue[index])
        }
        return Collections.unmodifiableList(tracks)
    }

    private fun safeCurrentIndex(): Int {
        return clampCurrentIndex()
    }

    private fun markQueueContentChanged() {
        queueRevision = if (queueRevision == Long.MAX_VALUE) 1L else queueRevision + 1L
    }

    private fun isAtEndOfQueue(): Boolean {
        val queue = this.queue
        return currentIndex() >= queue.size - 1
    }

    fun matchesMirroredQueue(itemCount: Int, matcher: QueueTrackMatcher): Boolean {
        val queue = this.queue
        if (itemCount != queue.size) {
            return false
        }
        for (i in queue.indices) {
            val track = queue[i] ?: return false
            if (!matcher.matches(i, track)) {
                return false
            }
        }
        return true
    }

    private object NoopQueuePlaybackActions : QueuePlaybackActions {
        override fun prepareCurrent(playWhenReady: Boolean) {}
        override fun publishState() {}
        override fun stopAndClear() {}
        override fun persistQueueAsync(tracks: List<Track>, currentIndex: Int): Boolean = false
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
