package app.echo.next

import app.echo.next.playback.EchoPlaybackService

internal fun interface PlaybackFloatProvider {
    fun get(): Float
}

internal fun interface PlaybackBooleanProvider {
    fun get(): Boolean
}

internal fun interface PlaybackServiceSetter {
    fun set(service: EchoPlaybackService?)
}

internal class PlaybackServiceHostBindings(
    private val playbackSpeedProvider: PlaybackFloatProvider,
    private val appVolumeProvider: PlaybackFloatProvider,
    private val concurrentPlaybackProvider: PlaybackBooleanProvider,
    private val playbackServiceSetter: PlaybackServiceSetter,
    private val resetPlaybackStoreAction: Runnable,
    private val playPendingTracksAction: Runnable,
    private val renderSelectedTabAction: Runnable,
    private val renderNowBarAction: Runnable
) : PlaybackServiceHostController.Host {
    override fun playbackSpeed(): Float = playbackSpeedProvider.get()

    override fun appVolume(): Float = appVolumeProvider.get()

    override fun concurrentPlaybackEnabled(): Boolean = concurrentPlaybackProvider.get()

    override fun attachPlaybackService(service: EchoPlaybackService) {
        playbackServiceSetter.set(service)
    }

    override fun clearPlaybackService() {
        playbackServiceSetter.set(null)
    }

    override fun resetPlaybackStore() {
        resetPlaybackStoreAction.run()
    }

    override fun playPendingTracksIfNeeded() {
        playPendingTracksAction.run()
    }

    override fun renderSelectedTab() {
        renderSelectedTabAction.run()
    }

    override fun renderNowBar() {
        renderNowBarAction.run()
    }
}
