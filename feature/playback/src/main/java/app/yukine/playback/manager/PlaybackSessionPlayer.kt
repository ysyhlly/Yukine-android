package app.yukine.playback.manager

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.yukine.model.Track
@UnstableApi
internal class PlaybackSessionPlayer(
    player: Player,
    private val delegate: Delegate
) : ForwardingPlayer(player) {

    companion object {
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
    }

    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun seekTo(positionMs: Long) = delegate.seekTo(positionMs)
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = delegate.seekTo(positionMs)

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

    override fun setMediaItem(mediaItem: MediaItem) {
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)) {
            super.setMediaItem(mediaItem)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, startPositionMs)) {
            super.setMediaItem(mediaItem, startPositionMs)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        val pos = if (resetPosition) 0L else C.TIME_UNSET
        if (!delegate.setControllerMediaItems(listOf(mediaItem), 0, pos)) {
            super.setMediaItem(mediaItem, resetPosition)
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!delegate.setControllerMediaItems(mediaItems, 0, C.TIME_UNSET)) {
            super.setMediaItems(mediaItems)
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        val pos = if (resetPosition) 0L else C.TIME_UNSET
        if (!delegate.setControllerMediaItems(mediaItems, 0, pos)) {
            super.setMediaItems(mediaItems, resetPosition)
        }
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
    ) {
        if (!delegate.setControllerMediaItems(mediaItems, startIndex, startPositionMs)) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
    }
}