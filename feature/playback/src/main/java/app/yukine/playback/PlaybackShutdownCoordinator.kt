package app.yukine.playback

internal class PlaybackShutdownCoordinator(
    private val playbackLyricsRelease: Runnable,
    private val playbackNotificationArtworkRelease: Runnable,
    private val playbackPrecacheRelease: Runnable,
    private val unregisterNoisyReceiver: Runnable,
    private val clearMainCallbacks: Runnable,
    private val shutdownTaskSchedulers: Runnable,
    private val releaseWifiLock: Runnable,
    private val releasePlayer: Runnable
) {
    fun releasePlaybackResources() {
        playbackLyricsRelease.run()
        releaseWifiLock.run()
        releasePlayer.run()
    }

    fun releaseServiceResources() {
        playbackLyricsRelease.run()
        unregisterNoisyReceiver.run()
        shutdownTaskSchedulers.run()
        clearMainCallbacks.run()
        playbackNotificationArtworkRelease.run()
        playbackPrecacheRelease.run()
        releaseWifiLock.run()
        releasePlayer.run()
    }
}
