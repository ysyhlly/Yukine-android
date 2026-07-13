package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track

/**
 * Semantic write boundary for application playback.
 *
 * Callers cannot observe or retain the concrete Service through this interface. Runtime state is
 * available only from [PlaybackReadModel].
 */
interface PlaybackCommands {
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(positionMs: Long)
    fun removeTracksById(trackIds: Set<Long>)
    fun clearQueue()
    fun moveQueueTrack(fromIndex: Int, toIndex: Int)
    fun replaceQueuedTrack(updated: Track)
    fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri) = Unit
    fun replaceQueuedTracks(updated: List<Track>) {
        updated.forEach(::replaceQueuedTrack)
    }
    fun replaceQueuedTrackById(oldTrackId: Long, updated: Track)
    fun retainTracksById(trackIds: Set<Long>)
    fun warmPlaybackTrack(track: Track)
    fun appendToQueue(tracks: List<Track>)
    fun replaceCurrentTrackAndResume(track: Track, positionMs: Long)
    fun replaceCurrentSourceAndResume(expectedTrackId: Long, track: Track, positionMs: Long) = Unit
    fun startSleepTimerMinutes(minutes: Int)
    fun cancelSleepTimer()
    fun playQueue(tracks: List<Track>, index: Int)
    fun pause()
    fun play()
    fun setShuffleEnabled(enabled: Boolean)
    fun cycleRepeatMode()
    fun setRepeatMode(repeatMode: Int)
}
