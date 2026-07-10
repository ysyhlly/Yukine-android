package app.yukine.playback.manager

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.yukine.model.Track
import java.util.concurrent.CopyOnWriteArraySet
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
        fun currentTrack(): Track?
        fun mediaMetadataForTrack(track: Track): MediaMetadata?
        fun positionMs(): Long
        fun sessionPositionMs(): Long
        fun durationMs(): Long
    }

    private var lastSessionPositionMs = Long.MIN_VALUE
    private var lastSessionPositionReadAtMs = Long.MIN_VALUE
    private val mediaMetadataListeners = CopyOnWriteArraySet<Player.Listener>()
    private val applicationLooper = player.applicationLooper
    private val metadataRefreshHandler = Handler(applicationLooper)

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        mediaMetadataListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        mediaMetadataListeners.remove(listener)
        super.removeListener(listener)
    }

    /**
     * Emits a metadata event for the dynamic metadata exposed by this session-only player.
     *
     * Media3 does not observe changes made only by [getMediaMetadata]. In particular, the
     * system-media lyric-title compatibility mode changes that getter without replacing the
     * underlying ExoPlayer media item. Dispatching the standard listener callback keeps system
     * surfaces current without restarting or otherwise disturbing audio playback.
     */
    fun refreshMediaMetadata() {
        if (mediaMetadataListeners.isEmpty()) {
            return
        }
        if (Looper.myLooper() == applicationLooper) {
            dispatchMediaMetadataChanged()
        } else {
            metadataRefreshHandler.post(::dispatchMediaMetadataChanged)
        }
    }

    private fun dispatchMediaMetadataChanged() {
        if (mediaMetadataListeners.isEmpty()) {
            return
        }
        val metadata = mediaMetadata
        mediaMetadataListeners.forEach { listener ->
            listener.onMediaMetadataChanged(metadata)
        }
    }

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
}
