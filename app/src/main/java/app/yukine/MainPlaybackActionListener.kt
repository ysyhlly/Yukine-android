package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface StreamingQueueResolveHandler {
    fun resolveCurrent(): Boolean
}

internal fun interface PlaybackSnapshotSource {
    fun snapshot(): PlaybackStateSnapshot?
}

internal fun interface PlaybackFallbackTracksSource {
    fun tracks(): List<Track>
}

internal fun interface PlaybackActionResultSink {
    fun apply(result: PlaybackActionResultUi?)
}

internal fun interface MainPlaybackActionListenerFactory {
    fun create(
        streamingResolver: StreamingQueueResolveHandler,
        snapshotSource: PlaybackSnapshotSource,
        fallbackTracksSource: PlaybackFallbackTracksSource,
        resultSink: PlaybackActionResultSink
    ): PlaybackActionController.Listener
}

internal class MainPlaybackActionListener(
    private val streamingResolver: StreamingQueueResolveHandler,
    private val snapshotSource: PlaybackSnapshotSource,
    private val fallbackTracksSource: PlaybackFallbackTracksSource,
    private val resultSink: PlaybackActionResultSink
) : PlaybackActionController.Listener {
    override fun resolveCurrentStreamingQueueTrackIfNeeded(): Boolean =
        streamingResolver.resolveCurrent()

    override fun playbackSnapshot(): PlaybackStateSnapshot? =
        snapshotSource.snapshot()

    override fun fallbackTracks(): List<Track> =
        fallbackTracksSource.tracks()

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        resultSink.apply(result)
    }
}
