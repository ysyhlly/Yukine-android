package app.echo.next

import android.content.Context
import android.content.Intent
import app.echo.next.model.Track
import app.echo.next.playback.EchoPlaybackService
import app.echo.next.playback.PlaybackStateSnapshot
import java.util.ArrayList
import java.util.HashSet

internal class PlaybackActionsController(
    private val context: Context
) {
    class Result(
        @JvmField val snapshot: PlaybackStateSnapshot?,
        @JvmField val status: String?,
        @JvmField val publishPlaybackState: Boolean,
        @JvmField val renderSelectedTab: Boolean,
        @JvmField val renderNowBar: Boolean,
        @JvmField val navigateNow: Boolean
    )

    fun skipToPrevious(service: EchoPlaybackService?) {
        if (service != null) {
            service.skipToPrevious()
            return
        }
        startPlaybackService(EchoPlaybackService.ACTION_PREVIOUS)
    }

    fun skipToNext(service: EchoPlaybackService?) {
        if (service != null) {
            service.skipToNext()
            return
        }
        startPlaybackService(EchoPlaybackService.ACTION_NEXT)
    }

    fun seekTo(service: EchoPlaybackService?, positionMs: Long) {
        service?.seekTo(positionMs)
    }

    fun removeQueueTrack(service: EchoPlaybackService?, track: Track?): Result {
        if (service == null) {
            return statusOnly("Queue is not connected")
        }
        if (track == null) {
            return statusOnly("Status")
        }
        val ids = HashSet<Long>()
        ids.add(track.id)
        service.removeTracksById(ids)
        return stateChanged(service.snapshot(), "Status: ${track.title}")
    }

    fun hasQueue(service: EchoPlaybackService?): Boolean {
        return service != null && service.queueSnapshot().isNotEmpty()
    }

    fun clearQueue(service: EchoPlaybackService?): Result {
        if (service == null) {
            return statusOnly("Queue is not connected")
        }
        service.clearQueue()
        return stateChanged(service.snapshot(), "Status")
    }

    fun replaceQueuedTrack(service: EchoPlaybackService?, oldTrackId: Long, updated: Track?) {
        if (service == null || updated == null) {
            return
        }
        if (oldTrackId == updated.id) {
            service.replaceQueuedTrack(updated)
            return
        }
        service.replaceQueuedTrackById(oldTrackId, updated)
    }

    fun retainTracks(service: EchoPlaybackService?, tracksToKeep: List<Track>?) {
        if (service == null || tracksToKeep == null) {
            return
        }
        val ids = HashSet<Long>()
        for (track in tracksToKeep) {
            ids.add(track.id)
        }
        service.retainTracksById(ids)
    }

    fun startSleepTimer(service: EchoPlaybackService?, minutes: Int): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        service.startSleepTimerMinutes(minutes)
        return Result(
            service.snapshot(),
            "Sleep timer set: $minutes minutes",
            false,
            true,
            true,
            false
        )
    }

    fun cancelSleepTimer(service: EchoPlaybackService?): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        service.cancelSleepTimer()
        return Result(
            service.snapshot(),
            "Sleep timer cancelled",
            false,
            true,
            true,
            false
        )
    }

    fun playTrackList(service: EchoPlaybackService?, tracks: List<Track>?, index: Int): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        if (tracks.isNullOrEmpty()) {
            return statusOnly("No tracks to play")
        }
        service.playQueue(ArrayList(tracks), index)
        return Result(service.snapshot(), null, false, false, true, false)
    }

    fun togglePlayback(
        service: EchoPlaybackService?,
        playbackState: PlaybackStateSnapshot?,
        fallbackTracks: List<Track>?
    ): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        if (playbackState != null && playbackState.playing) {
            service.pause()
        } else if (playbackState != null && playbackState.currentTrack != null) {
            startPlaybackService()
            service.play()
        } else if (!fallbackTracks.isNullOrEmpty()) {
            startPlaybackService()
            service.playQueue(ArrayList(fallbackTracks), 0)
        }
        return Result(service.snapshot(), null, false, false, true, false)
    }

    fun toggleShuffle(service: EchoPlaybackService?, playbackState: PlaybackStateSnapshot?): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        service.setShuffleEnabled(playbackState == null || !playbackState.shuffleEnabled)
        return Result(service.snapshot(), null, false, false, true, false)
    }

    fun cycleRepeat(service: EchoPlaybackService?): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        service.cycleRepeatMode()
        return Result(service.snapshot(), null, false, false, true, false)
    }

    fun cycleBottomPlaybackMode(service: EchoPlaybackService?, playbackState: PlaybackStateSnapshot?): Result {
        if (service == null) {
            return statusOnly("Playback service is not connected")
        }
        val shuffleEnabled = playbackState?.shuffleEnabled == true
        val repeatMode = playbackState?.repeatMode ?: EchoPlaybackService.REPEAT_ALL
        when {
            shuffleEnabled -> {
                service.setShuffleEnabled(false)
                service.setRepeatMode(EchoPlaybackService.REPEAT_ONE)
            }
            repeatMode == EchoPlaybackService.REPEAT_ONE -> {
                service.setRepeatMode(EchoPlaybackService.REPEAT_ALL)
            }
            else -> {
                service.setRepeatMode(EchoPlaybackService.REPEAT_ALL)
                service.setShuffleEnabled(true)
            }
        }
        return Result(service.snapshot(), null, false, false, true, false)
    }

    private fun startPlaybackService() {
        startPlaybackService(null)
    }

    private fun startPlaybackService(action: String?) {
        val intent = Intent(context, EchoPlaybackService::class.java)
        if (action != null) {
            intent.action = action
        }
        context.startService(intent)
    }

    private fun stateChanged(snapshot: PlaybackStateSnapshot, status: String): Result {
        return Result(snapshot, status, true, true, true, false)
    }

    private fun statusOnly(status: String): Result {
        return Result(null, status, false, false, false, false)
    }
}
