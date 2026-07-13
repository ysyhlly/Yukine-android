package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackCommands
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.service.PlaybackServiceActions
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.util.ArrayDeque
import javax.inject.Inject

internal class MainNowPlayingPlaybackGatewayFactory(
    private val serviceStarter: (String?) -> Unit,
    private val commandQueue: PlaybackServiceCommandQueue
) {
    fun create(
        serviceProvider: () -> PlaybackCommands?,
        playbackReadModelProvider: () -> PlaybackReadModel?
    ): NowPlayingPlaybackGateway {
        return NowPlayingPlaybackGatewayAdapter(
            serviceProvider,
            playbackReadModelProvider,
            serviceStarter,
            commandQueue
        )
    }
}

internal fun interface PlaybackServiceCommand {
    fun execute(service: PlaybackCommands)
}

/**
 * Owns commands issued during the short window between starting/binding playback and receiving
 * the service connection. The queue is activity-retained so it survives configuration changes,
 * but a finished Activity session cannot replay stale commands into a later session.
 */
@ActivityRetainedScoped
internal class PlaybackServiceCommandQueue @Inject constructor() {
    private val pending = ArrayDeque<PlaybackServiceCommand>()

    fun executeOrEnqueue(
        serviceProvider: () -> PlaybackCommands?,
        command: PlaybackServiceCommand
    ): Boolean {
        val service: PlaybackCommands
        val commands: List<PlaybackServiceCommand>
        synchronized(pending) {
            service = serviceProvider() ?: run {
                pending.addLast(command)
                return false
            }
            commands = drainLocked(command)
        }
        commands.forEach { it.execute(service) }
        return true
    }

    fun flush(service: PlaybackCommands) {
        val commands = synchronized(pending) { drainLocked() }
        commands.forEach { it.execute(service) }
    }

    internal fun pendingCount(): Int = synchronized(pending) { pending.size }

    private fun drainLocked(last: PlaybackServiceCommand? = null): List<PlaybackServiceCommand> {
        val commands = ArrayList<PlaybackServiceCommand>(pending.size + if (last == null) 0 else 1)
        while (pending.isNotEmpty()) {
            commands += pending.removeFirst()
        }
        if (last != null) {
            commands += last
        }
        return commands
    }
}

internal class NowPlayingPlaybackGatewayAdapter(
    private val serviceProvider: () -> PlaybackCommands?,
    private val playbackReadModelProvider: () -> PlaybackReadModel? = { null },
    private val serviceStarter: (String?) -> Unit,
    private val commandQueue: PlaybackServiceCommandQueue = PlaybackServiceCommandQueue()
) : NowPlayingPlaybackGateway {
    override fun serviceConnected(): Boolean {
        val readModel = playbackReadModelProvider()
        return if (readModel == null) {
            service() != null
        } else {
            readModel.connection.value == PlaybackConnectionState.Connected
        }
    }

    override fun snapshot(): PlaybackStateSnapshot? = playbackReadModelProvider()?.state?.value

    override fun queueSnapshot(): List<Track> = playbackReadModelProvider()?.queue?.value?.tracks.orEmpty()

    override fun skipToPrevious() {
        val service = service()
        if (service != null) {
            service.skipToPrevious()
        } else {
            serviceStarter(PlaybackServiceActions.PREVIOUS)
        }
    }

    override fun skipToNext() {
        val service = service()
        if (service != null) {
            service.skipToNext()
        } else {
            serviceStarter(PlaybackServiceActions.NEXT)
        }
    }

    override fun seekTo(positionMs: Long) {
        executeOrQueue { it.seekTo(positionMs) }
    }

    override fun removeTracksById(trackIds: Set<Long>) {
        executeOrQueue { it.removeTracksById(trackIds) }
    }

    override fun clearQueue() {
        executeOrQueue { it.clearQueue() }
    }

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        executeOrQueue { it.moveQueueTrack(fromIndex, toIndex) }
    }

    override fun replaceQueuedTrack(updated: Track) {
        executeOrQueue { it.replaceQueuedTrack(updated) }
    }

    override fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri) {
        executeOrQueue { it.updateQueuedTrackArtwork(trackId, artworkUri) }
    }

    override fun replaceQueuedTracks(updated: List<Track>) {
        executeOrQueue { it.replaceQueuedTracks(updated) }
    }

    override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
        executeOrQueue { it.replaceQueuedTrackById(oldTrackId, updated) }
    }

    override fun retainTracksById(trackIds: Set<Long>) {
        executeOrQueue { it.retainTracksById(trackIds) }
    }

    override fun warmPlaybackTrack(track: Track) {
        executeOrQueue { it.warmPlaybackTrack(track) }
    }

    override fun appendToQueue(tracks: List<Track>) {
        executeOrQueue { it.appendToQueue(tracks) }
    }

    override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
        executeOrQueue { it.replaceCurrentTrackAndResume(track, positionMs) }
    }

    override fun replaceCurrentSourceAndResume(expectedTrackId: Long, track: Track, positionMs: Long) {
        executeOrQueue { it.replaceCurrentSourceAndResume(expectedTrackId, track, positionMs) }
    }

    override fun startSleepTimerMinutes(minutes: Int) {
        executeOrQueue { it.startSleepTimerMinutes(minutes) }
    }

    override fun cancelSleepTimer() {
        executeOrQueue { it.cancelSleepTimer() }
    }

    override fun playQueue(tracks: List<Track>, index: Int) {
        serviceStarter(null)
        executeOrQueue { it.playQueue(tracks, index) }
    }

    override fun pause() {
        executeOrQueue { it.pause() }
    }

    override fun play() {
        serviceStarter(null)
        executeOrQueue { it.play() }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        executeOrQueue { it.setShuffleEnabled(enabled) }
    }

    override fun cycleRepeatMode() {
        executeOrQueue { it.cycleRepeatMode() }
    }

    override fun setRepeatMode(repeatMode: Int) {
        executeOrQueue { it.setRepeatMode(repeatMode) }
    }

    private fun executeOrQueue(command: PlaybackServiceCommand) {
        commandQueue.executeOrEnqueue(serviceProvider, command)
    }

    private fun service(): PlaybackCommands? = serviceProvider()
}
