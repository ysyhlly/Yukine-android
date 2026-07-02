package app.yukine

import app.yukine.model.Track

internal fun interface PlaybackStartHeartbeatStopper {
    fun stopHeartbeatRecommendationMode()
}

internal fun interface PlaybackStartServiceStarter {
    fun startPlaybackService()
}

internal fun interface PlaybackStartServiceAvailability {
    fun hasPlaybackService(): Boolean
}

internal fun interface PlaybackStartResolvingStatusProvider {
    fun resolvingStatus(): String
}

internal fun interface PlaybackStartStatusSink {
    fun setStatus(status: String)
}

internal fun interface PlaybackStartQueueOpener {
    fun openQueue()
}

internal fun interface MainPlaybackStartListenerFactory {
    fun create(
        heartbeatStopper: PlaybackStartHeartbeatStopper,
        serviceStarter: PlaybackStartServiceStarter,
        serviceAvailability: PlaybackStartServiceAvailability,
        resolvingStatusProvider: PlaybackStartResolvingStatusProvider,
        statusSink: PlaybackStartStatusSink,
        queueOpener: PlaybackStartQueueOpener
    ): MainPlaybackStartListener
}

internal class MainPlaybackStartListener(
    private val heartbeatStopper: PlaybackStartHeartbeatStopper,
    private val serviceStarter: PlaybackStartServiceStarter,
    private val serviceAvailability: PlaybackStartServiceAvailability,
    private val resolvingStatusProvider: PlaybackStartResolvingStatusProvider,
    private val statusSink: PlaybackStartStatusSink,
    private val queueOpener: PlaybackStartQueueOpener
) : PlaybackStartController.Listener {
    private var pendingTracks: List<Track> = emptyList()
    private var pendingIndex: Int = -1

    override fun stopHeartbeatRecommendationMode() {
        heartbeatStopper.stopHeartbeatRecommendationMode()
    }

    override fun startPlaybackService() {
        serviceStarter.startPlaybackService()
    }

    override fun hasPlaybackService(): Boolean =
        serviceAvailability.hasPlaybackService()

    override fun savePendingPlayback(tracks: List<Track>, index: Int) {
        pendingTracks = tracks.toList()
        pendingIndex = index
    }

    override fun pendingPlaybackTracks(): List<Track> =
        pendingTracks

    override fun pendingPlaybackIndex(): Int =
        pendingIndex

    override fun clearPendingPlayback() {
        pendingTracks = emptyList()
        pendingIndex = -1
    }

    override fun resolvingStatus(): String =
        resolvingStatusProvider.resolvingStatus()

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }

    override fun openQueue() {
        queueOpener.openQueue()
    }
}
