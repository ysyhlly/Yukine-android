package app.yukine

import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface PlaybackStateServiceProvider {
    fun service(): EchoPlaybackService?
}

internal fun interface CurrentLyricsTrackIdProvider {
    fun trackId(): Long
}

internal fun interface PlaybackSettingsSaver {
    fun save(playbackSpeed: Float, appVolume: Float)
}

internal fun interface PlaybackTrackAction {
    fun run(track: Track?)
}

internal fun interface PlaybackSnapshotAction {
    fun run(snapshot: PlaybackStateSnapshot)
}

internal class PlaybackServiceQueueSourceBindings(
    private val serviceProvider: PlaybackStateServiceProvider
) : PlaybackStateEventController.ServiceQueueSource {
    override fun service(): EchoPlaybackService? {
        return serviceProvider.service()
    }
}

internal class PlaybackStateEventBindings(
    private val selectedTabProvider: SettingsSelectedTabProvider,
    private val currentLyricsTrackIdProvider: CurrentLyricsTrackIdProvider,
    private val playbackSettingsSaver: PlaybackSettingsSaver,
    private val loadLyricsAction: PlaybackTrackAction,
    private val loadCollectionsAction: Runnable,
    private val renderNowBarAction: Runnable,
    private val updateHomeDashboardPlaybackAction: PlaybackSnapshotAction,
    private val renderSelectedTabAction: Runnable,
    private val updateNowPlayingContentAction: Runnable,
    private val preResolveNextStreamingTrackAction: PlaybackSnapshotAction,
    private val recoverStreamingBufferingAction: PlaybackSnapshotAction,
    private val statusSink: SettingsStatusSink
) : PlaybackStateEventController.Listener {
    override fun selectedTab(): String {
        return selectedTabProvider.selectedTab()
    }

    override fun currentLyricsTrackId(): Long {
        return currentLyricsTrackIdProvider.trackId()
    }

    override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) {
        playbackSettingsSaver.save(playbackSpeed, appVolume)
    }

    override fun loadLyrics(track: Track?) {
        loadLyricsAction.run(track)
    }

    override fun loadCollections() {
        loadCollectionsAction.run()
    }

    override fun renderNowBar() {
        renderNowBarAction.run()
    }

    override fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot) {
        updateHomeDashboardPlaybackAction.run(snapshot)
    }

    override fun renderSelectedTab() {
        renderSelectedTabAction.run()
    }

    override fun updateNowPlayingContent() {
        updateNowPlayingContentAction.run()
    }

    override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) {
        preResolveNextStreamingTrackAction.run(snapshot)
    }

    override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) {
        recoverStreamingBufferingAction.run(snapshot)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
