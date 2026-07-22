package app.yukine

import android.os.Handler
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
import app.yukine.streaming.StreamingPlaybackAdapter

/**
 * Coalesces high-frequency service snapshots and owns the background domain reactions caused by
 * a meaningful playback transition. UI rendering observes [app.yukine.playback.PlaybackReadModel]
 * directly and is intentionally absent from this boundary.
 */
internal class PlaybackDomainReactionOwner(
    private val mainHandler: Handler,
    private val currentLyricsTrackIdSource: CurrentLyricsTrackIdSource,
    private val playbackSettingsSaver: PlaybackSettingsSaver,
    private val lyricsLoader: LyricsLoader,
    private val collectionsLoader: CollectionsLoader,
    private val nextStreamingTrackPreResolver: NextStreamingTrackPreResolver,
    private val streamingBufferingRecoveryHandler: StreamingBufferingRecoveryHandler,
    private val currentStreamingTrackResolver: CurrentStreamingTrackResolver,
    private val statusSink: StatusSink
) : PlaybackStateListener {
    fun interface CurrentLyricsTrackIdSource {
        fun currentLyricsTrackId(): Long
    }

    fun interface PlaybackSettingsSaver {
        fun save(playbackSpeed: Float, appVolume: Float)
    }

    fun interface LyricsLoader {
        fun load(track: Track?)
    }

    fun interface CollectionsLoader {
        fun load()
    }

    fun interface NextStreamingTrackPreResolver {
        fun preResolve(snapshot: PlaybackStateSnapshot)
    }

    fun interface StreamingBufferingRecoveryHandler {
        fun recover(snapshot: PlaybackStateSnapshot)
    }

    fun interface CurrentStreamingTrackResolver {
        fun resolveIfNeeded(): Boolean
    }

    fun interface StatusSink {
        fun set(status: String)
    }

    private var lastAutoResolveTrackId = -1L
    private var lastHistoryRefreshTrackId = -1L
    private val stateDispatchLock = Any()
    private var pendingStateSnapshot: PlaybackStateSnapshot? = null
    private var stateDispatchPosted = false
    private val bufferingDispatchLock = Any()
    private var pendingBufferingSnapshot: PlaybackStateSnapshot? = null
    private var bufferingDispatchPosted = false
    private val stateDispatch = Runnable {
        val snapshot = synchronized(stateDispatchLock) {
            pendingStateSnapshot.also {
                pendingStateSnapshot = null
                stateDispatchPosted = false
            }
        }
        snapshot?.let(::handlePlaybackState)
    }
    private val bufferingDispatch = Runnable {
        val snapshot = synchronized(bufferingDispatchLock) {
            pendingBufferingSnapshot.also {
                pendingBufferingSnapshot = null
                bufferingDispatchPosted = false
            }
        }
        snapshot?.let(streamingBufferingRecoveryHandler::recover)
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
        val shouldPost = synchronized(bufferingDispatchLock) {
            pendingBufferingSnapshot = snapshot
            if (bufferingDispatchPosted) {
                false
            } else {
                bufferingDispatchPosted = true
                true
            }
        }
        if (shouldPost && !mainHandler.post(bufferingDispatch)) {
            synchronized(bufferingDispatchLock) {
                pendingBufferingSnapshot = null
                bufferingDispatchPosted = false
            }
        }
    }

    private fun handlePlaybackState(snapshot: PlaybackStateSnapshot) {
        playbackSettingsSaver.save(snapshot.playbackSpeed, snapshot.appVolume)
        val result = PlaybackStateUpdateController.resolve(
            snapshot,
            currentLyricsTrackIdSource.currentLyricsTrackId(),
            lastHistoryRefreshTrackId
        )
        lastHistoryRefreshTrackId = result.lastHistoryRefreshTrackId
        if (result.loadLyrics) {
            lyricsLoader.load(snapshot.currentTrack)
        }
        if (result.refreshCollections) {
            collectionsLoader.load()
        }
        nextStreamingTrackPreResolver.preResolve(snapshot)
        if (result.showError) {
            // 流媒体地址可能在后台或冷启动恢复后过期。任何流媒体播放错误都先自动刷新
            // 当前 URL；占位曲和已解析但已失效的临时地址走同一条恢复路径。
            if (StreamingPlaybackAdapter.isStreamingTrack(snapshot.currentTrack)) {
                val trackId = snapshot.currentTrack?.id ?: -1L
                if (trackId != lastAutoResolveTrackId && currentStreamingTrackResolver.resolveIfNeeded()) {
                    lastAutoResolveTrackId = trackId
                    return
                }
            }
            statusSink.set(snapshot.errorMessage)
        } else if (!StreamingPlaybackAdapter.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
            // 一旦当前曲目可正常播放，重置自动解析去重标记，便于地址后续再次失效时恢复。
            lastAutoResolveTrackId = -1L
        }
    }
}
