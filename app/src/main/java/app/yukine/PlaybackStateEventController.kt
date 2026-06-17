package app.yukine

import android.os.Handler
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateListener
import app.yukine.playback.PlaybackStateSnapshot

internal class PlaybackStateEventController(
    private val mainHandler: Handler,
    private val playbackStore: MainPlaybackStore,
    private val updateController: PlaybackStateUpdateController,
    private val serviceQueueSource: ServiceQueueSource,
    private val listener: Listener
) : PlaybackStateListener {
    interface Listener {
        fun selectedTab(): String

        fun currentLyricsTrackId(): Long

        fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float)

        fun loadLyrics(track: Track?)

        fun loadCollections()

        fun renderNowBar()

        fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot)

        fun renderSelectedTab()

        fun updateNowPlayingContent()

        fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot)

        fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot)

        fun setStatus(status: String)
    }

    interface ServiceQueueSource {
        fun service(): EchoPlaybackService?
    }

    override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
        mainHandler.post {
            handlePlaybackState(snapshot)
        }
    }

    override fun onPlaybackBuffering(snapshot: PlaybackStateSnapshot) {
        mainHandler.post {
            listener.recoverStreamingBuffering(snapshot)
        }
    }

    private fun handlePlaybackState(snapshot: PlaybackStateSnapshot) {
        val previous = playbackStore.replaceSnapshot(snapshot)
        listener.savePlaybackSettings(snapshot.playbackSpeed, snapshot.appVolume)
        val result = updateController.resolve(
            listener.selectedTab(),
            previous,
            snapshot,
            listener.currentLyricsTrackId(),
            playbackStore.lastHistoryRefreshTrackId()
        )
        playbackStore.setLastHistoryRefreshTrackId(result.lastHistoryRefreshTrackId)
        if (result.loadLyrics) {
            listener.loadLyrics(snapshot.currentTrack)
        }
        if (result.refreshCollections) {
            listener.loadCollections()
        }
        playbackStore.publish(serviceQueueSource.service())
        listener.renderNowBar()
        listener.updateHomeDashboardPlayback(snapshot)
        listener.preResolveNextStreamingTrack(snapshot)
        if (result.renderSelectedTab) {
            listener.renderSelectedTab()
        } else if (result.updateNowPlaying) {
            listener.updateNowPlayingContent()
        }
        if (result.showError) {
            listener.setStatus(snapshot.errorMessage)
        }
    }
}
