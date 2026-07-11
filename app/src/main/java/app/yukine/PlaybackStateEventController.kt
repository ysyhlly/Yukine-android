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
    private var lastPublishedQueueKey = QueueKey.empty()
    private val stateDispatchLock = Any()
    private var pendingStateSnapshot: PlaybackStateSnapshot? = null
    private var stateDispatchPosted = false
    private val stateDispatch = Runnable {
        val snapshot = synchronized(stateDispatchLock) {
            pendingStateSnapshot.also {
                pendingStateSnapshot = null
                stateDispatchPosted = false
            }
        }
        if (snapshot != null) {
            handlePlaybackState(snapshot)
        }
    }

    interface Listener {
        fun selectedTab(): String

        fun queueVisible(): Boolean

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
        val shouldPost = synchronized(stateDispatchLock) {
            pendingStateSnapshot = snapshot
            if (stateDispatchPosted) {
                false
            } else {
                stateDispatchPosted = true
                true
            }
        }
        if (shouldPost && !mainHandler.post(stateDispatch)) {
            synchronized(stateDispatchLock) {
                pendingStateSnapshot = null
                stateDispatchPosted = false
            }
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
        publishQueueIfChanged(snapshot)
        listener.renderNowBar()
        listener.updateHomeDashboardPlayback(snapshot)
        listener.preResolveNextStreamingTrack(snapshot)
        if (result.renderSelectedTab) {
            listener.renderSelectedTab()
        } else if (result.updateNowPlaying) {
            listener.updateNowPlayingContent()
        }
        if (result.showError) {
            // 流媒体地址可能在后台或冷启动恢复后过期。任何流媒体播放错误都先自动刷新
            // 当前 URL；占位曲和已解析但已失效的临时地址走同一条恢复路径。
            if (StreamingPlaybackAdapter.isStreamingTrack(snapshot.currentTrack)) {
                val trackId = snapshot.currentTrack?.id ?: -1L
                if (trackId != lastAutoResolveTrackId && listener.resolveCurrentStreamingTrackIfNeeded()) {
                    lastAutoResolveTrackId = trackId
                    return
                }
            }
            listener.setStatus(snapshot.errorMessage)
        } else {
            // 一旦当前曲目可正常播放，重置自动解析去重标记，便于地址后续再次失效时恢复。
            if (!StreamingPlaybackAdapter.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
                lastAutoResolveTrackId = -1L
            }
        }
    }

    private fun publishQueueIfChanged(snapshot: PlaybackStateSnapshot) {
        if (!listener.queueVisible()) {
            return
        }
        val nextKey = QueueKey.from(snapshot)
        if (nextKey == lastPublishedQueueKey) {
            return
        }
        lastPublishedQueueKey = nextKey
        playbackStore.publish(queueSnapshotSource.queueSnapshot())
    }

    private data class QueueKey(
        val currentTrackId: Long,
        val currentIndex: Int,
        val queueSize: Int,
        val queueRevision: Long
    ) {
        companion object {
            fun empty(): QueueKey = QueueKey(-1L, -1, 0, 0L)

            fun from(snapshot: PlaybackStateSnapshot): QueueKey =
                QueueKey(
                    snapshot.currentTrack?.id ?: -1L,
                    snapshot.currentIndex,
                    snapshot.queueSize,
                    snapshot.queueRevision
                )
        }
    }
}
