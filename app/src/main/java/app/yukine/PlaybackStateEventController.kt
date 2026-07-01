package app.yukine

import android.os.Handler
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
import app.yukine.streaming.StreamingPlaybackAdapter

internal class PlaybackStateEventController(
    private val mainHandler: Handler,
    private val playbackStore: MainPlaybackStore,
    private val queueSnapshotSource: QueueSnapshotSource,
    private val listener: Listener
) : PlaybackStateListener {
    private var lastAutoResolveTrackId = -1L

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

        /**
         * Triggers on-demand resolution of the current streaming queue track when it is still an
         * unresolved placeholder. Returns true when a resolve was scheduled (so the stale
         * "tap again" error can be suppressed).
         */
        fun resolveCurrentStreamingTrackIfNeeded(): Boolean

        fun setStatus(status: String)
    }

    interface QueueSnapshotSource {
        fun queueSnapshot(): List<Track>
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
        val result = PlaybackStateUpdateController.resolve(
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
        playbackStore.publish(queueSnapshotSource.queueSnapshot())
        listener.renderNowBar()
        listener.updateHomeDashboardPlayback(snapshot)
        listener.preResolveNextStreamingTrack(snapshot)
        if (result.renderSelectedTab) {
            listener.renderSelectedTab()
        } else if (result.updateNowPlaying) {
            listener.updateNowPlayingContent()
        }
        if (result.showError) {
            // 当前曲目仍是未解析的流媒体占位（自动切歌/恢复时常见）：自动触发解析并吞掉
            // “请重新点击歌曲”这条过期提示，而不是要求用户再点一次。
            if (StreamingPlaybackAdapter.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
                val trackId = snapshot.currentTrack?.id ?: -1L
                if (trackId != lastAutoResolveTrackId && listener.resolveCurrentStreamingTrackIfNeeded()) {
                    lastAutoResolveTrackId = trackId
                    return
                }
            }
            listener.setStatus(snapshot.errorMessage)
        } else {
            // 一旦当前曲目可正常播放，重置自动解析去重标记，便于后续占位曲再次自动解析。
            if (!StreamingPlaybackAdapter.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
                lastAutoResolveTrackId = -1L
            }
        }
    }
}
