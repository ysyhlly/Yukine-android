package app.yukine.playback.manager

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.yukine.model.Track
import java.util.function.LongSupplier

@UnstableApi
internal class PlaybackSessionPlayer @JvmOverloads constructor(
    player: Player,
    private val delegate: Delegate,
    private val elapsedRealtimeMs: LongSupplier = LongSupplier { SystemClock.elapsedRealtime() }
) : ForwardingPlayer(player) {

    companion object {
        private const val SESSION_POSITION_REFRESH_MS = 1_000L

        private fun isAppQueueNavigationCommand(command: Int): Boolean =
            command == Player.COMMAND_SEEK_TO_PREVIOUS
                    || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    || command == Player.COMMAND_SEEK_TO_NEXT
                    || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM

        private fun appRepeatModeForMedia3RepeatMode(media3RepeatMode: Int): Int = when (media3RepeatMode) {
            Player.REPEAT_MODE_ONE -> 1
            Player.REPEAT_MODE_OFF -> 2
            else -> 0
        }

        private fun safeSeekPosition(positionMs: Long): Long = maxOf(0L, positionMs)
    }

    interface Delegate {
        fun play()
        fun pause()
        fun seekTo(positionMs: Long)
        fun skipToPrevious()
        fun skipToNext()
        fun setRepeatMode(appRepeatMode: Int)
        fun stopAndClear()
        fun setControllerMediaItems(
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long
        ): Boolean
        fun sessionQueueTracks(): List<Track>
        fun sessionQueueSize(): Int
        fun sessionQueueCurrentIndex(): Int
        fun sessionQueueTrackAt(index: Int): Track?
        fun currentTrack(): Track?
        fun mediaMetadataForTrack(track: Track): MediaMetadata?
        fun positionMs(): Long
        fun sessionPositionMs(): Long
        fun durationMs(): Long
    }

    private var lastSessionPositionMs = Long.MIN_VALUE
    private var lastSessionPositionReadAtMs = Long.MIN_VALUE
    private var cachedSessionQueueKey: SessionQueueKey? = null
    private var cachedSessionQueueTimeline: Timeline? = null

    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun seekTo(positionMs: Long) {
        val targetPositionMs = safeSeekPosition(positionMs)
        delegate.seekTo(targetPositionMs)
        seedSessionPosition(targetPositionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val targetPositionMs = safeSeekPosition(positionMs)
        delegate.seekTo(targetPositionMs)
        seedSessionPosition(targetPositionMs)
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (isAppQueueNavigationCommand(command)
            || command == Player.COMMAND_SET_REPEAT_MODE) return true
        return super.isCommandAvailable(command)
    }

    override fun getAvailableCommands(): Player.Commands =
        Player.Commands.Builder()
            .addAll(super.getAvailableCommands())
            .addAll(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE
            ).build()

    override fun seekToPrevious() = delegate.skipToPrevious()
    override fun seekToPreviousMediaItem() = delegate.skipToPrevious()
    override fun seekToNext() = delegate.skipToNext()
    override fun seekToNextMediaItem() = delegate.skipToNext()

    override fun setRepeatMode(repeatMode: Int) {
        delegate.setRepeatMode(appRepeatModeForMedia3RepeatMode(repeatMode))
    }

    override fun stop() = delegate.stopAndClear()

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) delegate.play() else delegate.pause()
    }

    override fun getMediaMetadata(): MediaMetadata {
        val track = delegate.currentTrack()
        if (track != null) {
            val metadata = delegate.mediaMetadataForTrack(track)
            if (metadata != null) return metadata
        }
        return super.getMediaMetadata()
    }

    override fun getCurrentTimeline(): Timeline {
        val timeline = sessionQueueTimeline()
        return timeline ?: super.getCurrentTimeline()
    }

    override fun getCurrentMediaItemIndex(): Int {
        val index = sessionQueueIndex()
        return if (index >= 0) index else super.getCurrentMediaItemIndex()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val mediaItem = mediaItemAt(sessionQueueIndex())
        return mediaItem ?: super.getCurrentMediaItem()
    }

    override fun getMediaItemCount(): Int {
        val size = delegate.sessionQueueSize()
        return if (size > 0) size else super.getMediaItemCount()
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mediaItemAt(index) ?: super.getMediaItemAt(index)
    }

    override fun getCurrentPosition(): Long {
        val nowMs = maxOf(0L, elapsedRealtimeMs.asLong)
        val currentPositionMs = maxOf(0L, delegate.sessionPositionMs())
        if (lastSessionPositionMs == Long.MIN_VALUE ||
            currentPositionMs < lastSessionPositionMs ||
            currentPositionMs - lastSessionPositionMs >= SESSION_POSITION_REFRESH_MS ||
            nowMs - lastSessionPositionReadAtMs >= SESSION_POSITION_REFRESH_MS
        ) {
            lastSessionPositionMs = currentPositionMs
            lastSessionPositionReadAtMs = nowMs
        }
        return maxOf(0L, lastSessionPositionMs)
    }

    override fun getContentPosition(): Long = currentPosition

    override fun getDuration(): Long {
        val durationMs = delegate.durationMs()
        return if (durationMs <= 0L) super.getDuration() else durationMs
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        resetSessionPosition()
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)) {
            super.setMediaItem(mediaItem)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        seedSessionPosition(startPositionMs)
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, startPositionMs)) {
            super.setMediaItem(mediaItem, startPositionMs)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        val pos = if (resetPosition) 0L else C.TIME_UNSET
        seedSessionPosition(pos)
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, pos)) {
            super.setMediaItem(mediaItem, resetPosition)
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        resetSessionPosition()
        if (!delegate.setControllerMediaItems(mediaItems, 0, C.TIME_UNSET)) {
            super.setMediaItems(mediaItems)
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        val pos = if (resetPosition) 0L else C.TIME_UNSET
        seedSessionPosition(pos)
        if (!delegate.setControllerMediaItems(mediaItems, 0, pos)) {
            super.setMediaItems(mediaItems, resetPosition)
        }
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
    ) {
        seedSessionPosition(startPositionMs)
        if (!delegate.setControllerMediaItems(mediaItems, startIndex, startPositionMs)) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
    }

    private fun seedSessionPosition(positionMs: Long) {
        if (positionMs == C.TIME_UNSET) {
            resetSessionPosition()
            return
        }
        lastSessionPositionMs = maxOf(0L, positionMs)
        lastSessionPositionReadAtMs = maxOf(0L, elapsedRealtimeMs.asLong)
    }

    private fun resetSessionPosition() {
        lastSessionPositionMs = Long.MIN_VALUE
        lastSessionPositionReadAtMs = Long.MIN_VALUE
    }

    private fun sessionQueueTimeline(): Timeline? {
        val key = sessionQueueKey()
        if (key == null) {
            cachedSessionQueueKey = null
            cachedSessionQueueTimeline = null
            return null
        }
        if (cachedSessionQueueKey == key && cachedSessionQueueTimeline != null) {
            return cachedSessionQueueTimeline
        }
        val tracks = delegate.sessionQueueTracks()
        if (tracks.isEmpty()) {
            return null
        }
        return SessionQueueTimeline(tracks.mapIndexed { index, track -> mediaItemForTrack(index, track) }).also {
            cachedSessionQueueKey = key
            cachedSessionQueueTimeline = it
        }
    }

    private fun mediaItemAt(index: Int): MediaItem? {
        if (index < 0 || index >= delegate.sessionQueueSize()) {
            return null
        }
        val track = delegate.sessionQueueTrackAt(index) ?: return null
        return mediaItemForTrack(index, track)
    }

    private fun mediaItemForTrack(index: Int, track: Track): MediaItem {
        val metadata = delegate.mediaMetadataForTrack(track)
            ?: MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build()
        return MediaItem.Builder()
            .setMediaId("queue:$index:${track.id}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun sessionQueueTracks(): List<Track> {
        val tracks = delegate.sessionQueueTracks()
        return if (tracks.isEmpty()) emptyList() else tracks
    }

    private fun sessionQueueIndex(): Int {
        val size = delegate.sessionQueueSize()
        val index = delegate.sessionQueueCurrentIndex()
        return if (index in 0 until size) index else -1
    }

    private fun sessionQueueKey(): SessionQueueKey? {
        val size = delegate.sessionQueueSize()
        if (size <= 0) {
            return null
        }
        val index = sessionQueueIndex()
        val firstId = delegate.sessionQueueTrackAt(0)?.id ?: -1L
        val currentId = delegate.sessionQueueTrackAt(index)?.id ?: -1L
        val lastId = delegate.sessionQueueTrackAt(size - 1)?.id ?: -1L
        return SessionQueueKey(size, index, firstId, currentId, lastId, sessionQueueFingerprint(size, index))
    }

    private fun sessionQueueFingerprint(size: Int, currentIndex: Int): Long {
        var fingerprint = 0xcbf29ce484222325UL.toLong()
        for (sampleIndex in sessionQueueFingerprintSampleIndices(size, currentIndex)) {
            val trackId = delegate.sessionQueueTrackAt(sampleIndex)?.id ?: -1L
            fingerprint = (fingerprint xor sampleIndex.toLong()) * 0x100000001b3L
            fingerprint = (fingerprint xor trackId) * 0x100000001b3L
        }
        return fingerprint
    }

    private fun sessionQueueFingerprintSampleIndices(size: Int, currentIndex: Int): List<Int> {
        if (size <= 0) {
            return emptyList()
        }
        if (size <= 32) {
            return (0 until size).toList()
        }
        val indices = LinkedHashSet<Int>()
        fun add(index: Int) {
            if (index in 0 until size) {
                indices.add(index)
            }
        }
        add(0)
        add(size - 1)
        for (offset in -3..3) {
            add(currentIndex + offset)
        }
        val fractions = intArrayOf(4, 3, 2)
        for (divisor in fractions) {
            add(size / divisor)
            add(size - 1 - (size / divisor))
        }
        val step = maxOf(1, size / 8)
        var index = step
        while (index < size - 1) {
            add(index)
            index += step
        }
        return indices.toList()
    }

    private data class SessionQueueKey(
        val size: Int,
        val currentIndex: Int,
        val firstTrackId: Long,
        val currentTrackId: Long,
        val lastTrackId: Long,
        val sampledFingerprint: Long
    )

    private class SessionQueueTimeline(private val mediaItems: List<MediaItem>) : Timeline() {
        override fun getWindowCount(): Int = mediaItems.size

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long
        ): Window {
            val mediaItem = mediaItems[windowIndex]
            return window.set(
                "queue-window-$windowIndex",
                mediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                true,
                false,
                null,
                0L,
                C.TIME_UNSET,
                windowIndex,
                windowIndex,
                0L
            )
        }

        override fun getPeriodCount(): Int = mediaItems.size

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            val uid = "queue-period-$periodIndex"
            return period.set(
                if (setIds) uid else null,
                if (setIds) uid else null,
                periodIndex,
                C.TIME_UNSET,
                0L
            )
        }

        override fun getIndexOfPeriod(uid: Any): Int {
            val value = uid.toString().removePrefix("queue-period-").toIntOrNull() ?: return C.INDEX_UNSET
            return if (value in mediaItems.indices) value else C.INDEX_UNSET
        }

        override fun getUidOfPeriod(periodIndex: Int): Any = "queue-period-$periodIndex"
    }
}
