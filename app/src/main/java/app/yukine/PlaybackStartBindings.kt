package app.yukine

import app.yukine.model.Track

internal fun interface PlaybackStartServiceStarter {
    fun start()
}

internal fun interface PlaybackServiceAvailability {
    fun available(): Boolean
}

internal fun interface PendingPlaybackSaver {
    fun save(tracks: List<Track>, index: Int)
}

internal fun interface PendingPlaybackTracksProvider {
    fun tracks(): List<Track>
}

internal fun interface PendingPlaybackIndexProvider {
    fun index(): Int
}

internal fun interface StreamingTrackListResolver {
    fun resolve(tracks: List<Track>?, index: Int): Boolean
}

internal fun interface PlaybackTrackListPlayer {
    fun play(tracks: List<Track>?, index: Int): PlaybackActionResultUi?
}

internal class PlaybackStartBindings(
    private val heartbeatRecommendationStopper: QueueNoArgAction,
    private val playbackServiceStarter: PlaybackStartServiceStarter,
    private val playbackServiceAvailability: PlaybackServiceAvailability,
    private val pendingPlaybackSaver: PendingPlaybackSaver,
    private val pendingPlaybackTracksProvider: PendingPlaybackTracksProvider,
    private val pendingPlaybackIndexProvider: PendingPlaybackIndexProvider,
    private val pendingPlaybackClearer: QueueNoArgAction,
    private val resolvingStatusProvider: QueueStatusProvider,
    private val statusSink: QueueStatusSink,
    private val streamingTrackListResolver: StreamingTrackListResolver,
    private val playbackTrackListPlayer: PlaybackTrackListPlayer,
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier
) : PlaybackStartController.Listener {
    override fun stopHeartbeatRecommendationMode() {
        heartbeatRecommendationStopper.run()
    }

    override fun startPlaybackService() {
        playbackServiceStarter.start()
    }

    override fun hasPlaybackService(): Boolean {
        return playbackServiceAvailability.available()
    }

    override fun savePendingPlayback(tracks: List<Track>, index: Int) {
        pendingPlaybackSaver.save(tracks, index)
    }

    override fun pendingPlaybackTracks(): List<Track> {
        return pendingPlaybackTracksProvider.tracks()
    }

    override fun pendingPlaybackIndex(): Int {
        return pendingPlaybackIndexProvider.index()
    }

    override fun clearPendingPlayback() {
        pendingPlaybackClearer.run()
    }

    override fun resolvingStatus(): String {
        return resolvingStatusProvider.status()
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }

    override fun resolveAndPlayStreamingTrack(tracks: List<Track>?, index: Int): Boolean {
        return streamingTrackListResolver.resolve(tracks, index)
    }

    override fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi? {
        return playbackTrackListPlayer.play(tracks, index)
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        playbackActionResultApplier.apply(result)
    }
}
