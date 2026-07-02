package app.yukine.playback

internal class PlaybackShutdownCoordinator(
    private val playbackResources: PlaybackResources,
    private val serviceResources: ServiceResources,
    private val lifecycleResources: LifecycleResources
) {
    interface PlaybackResources {
        fun releaseLyrics()
        fun releaseWifiLock()
        fun releasePlayer()
    }

    interface ServiceResources {
        fun unregisterNoisyReceiver()
        fun releaseWarmup()
        fun releaseVisualizationAnalyzer()
        fun releaseRecoveryScheduler()
        fun shutdownTaskSchedulers()
        fun releaseErrorRecovery()
        fun releaseProgressUpdates()
        fun releaseSleepTimer()
        fun releaseCrossfade()
        fun clearMainCallbacks()
        fun releaseVisualizationCache()
        fun releaseNotificationArtwork()
        fun releasePrecache()
        fun releaseStatePublisher()
    }

    interface LifecycleResources {
        fun persistPlaybackPosition()
        fun persistPlaybackQueue()
        fun savePlaybackResumeRequested(requested: Boolean)
        fun isPlaying(): Boolean
        fun isPreparing(): Boolean
        fun hasNotificationWorthyState(): Boolean
        fun publishPlaybackNotification()
    }

    private var lyricsReleased = false
    private var transportResourcesReleased = false
    private var serviceResourcesReleased = false

    fun releasePlaybackResources() {
        releaseLyricsOnce()
        releaseTransportResourcesOnce()
    }

    private fun releaseLyricsOnce() {
        if (lyricsReleased) {
            return
        }
        lyricsReleased = true
        playbackResources.releaseLyrics()
    }

    private fun releaseTransportResourcesOnce() {
        if (transportResourcesReleased) {
            return
        }
        transportResourcesReleased = true
        playbackResources.releaseWifiLock()
        playbackResources.releasePlayer()
    }

    fun handleTaskRemoved() {
        lifecycleResources.persistPlaybackPosition()
        lifecycleResources.persistPlaybackQueue()
        lifecycleResources.savePlaybackResumeRequested(
            lifecycleResources.isPlaying() || lifecycleResources.isPreparing()
        )
        if (lifecycleResources.hasNotificationWorthyState()) {
            lifecycleResources.publishPlaybackNotification()
        }
    }

    fun handleServiceDestroyed() {
        if (serviceResourcesReleased) {
            return
        }
        serviceResourcesReleased = true
        lifecycleResources.persistPlaybackPosition()
        releaseServiceResources()
    }

    private fun releaseServiceResources() {
        releaseLyricsOnce()
        serviceResources.unregisterNoisyReceiver()
        serviceResources.releaseWarmup()
        serviceResources.releaseVisualizationAnalyzer()
        serviceResources.releaseRecoveryScheduler()
        serviceResources.shutdownTaskSchedulers()
        serviceResources.releasePrecache()
        serviceResources.releaseErrorRecovery()
        serviceResources.releaseProgressUpdates()
        serviceResources.releaseSleepTimer()
        serviceResources.releaseCrossfade()
        serviceResources.clearMainCallbacks()
        serviceResources.releaseVisualizationCache()
        serviceResources.releaseNotificationArtwork()
        serviceResources.releaseStatePublisher()
        releaseTransportResourcesOnce()
    }
}
