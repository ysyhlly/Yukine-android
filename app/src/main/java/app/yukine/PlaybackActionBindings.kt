package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface StreamingQueueResolveAction {
    fun resolve(): Boolean
}

internal fun interface PlaybackSnapshotProvider {
    fun snapshot(): PlaybackStateSnapshot?
}

internal fun interface PlaybackFallbackTracksProvider {
    fun tracks(): List<Track>
}

internal class PlaybackActionBindings(
    private val streamingQueueResolveAction: StreamingQueueResolveAction,
    private val playbackSnapshotProvider: PlaybackSnapshotProvider,
    private val fallbackTracksProvider: PlaybackFallbackTracksProvider,
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier
) : PlaybackActionController.Listener {
    override fun resolveCurrentStreamingQueueTrackIfNeeded(): Boolean {
        return streamingQueueResolveAction.resolve()
    }

    override fun playbackSnapshot(): PlaybackStateSnapshot? {
        return playbackSnapshotProvider.snapshot()
    }

    override fun fallbackTracks(): List<Track> {
        return fallbackTracksProvider.tracks()
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        playbackActionResultApplier.apply(result)
    }
}
