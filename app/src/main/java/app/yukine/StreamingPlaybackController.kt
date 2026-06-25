package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality

internal class StreamingPlaybackController(
    private val streamingViewModel: StreamingViewModel,
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun languageMode(): String

        fun adaptiveStreamingQuality(): StreamingAudioQuality

        fun selectedStreamingQuality(): StreamingAudioQuality

        fun queueSnapshot(): List<Track>

        fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot)

        fun applyPlaybackActionResult(result: PlaybackActionResultUi?)

        fun setStatus(status: String)
    }

    fun resolveAndPlayStreamingTrack(tracks: List<Track>?, index: Int): Boolean {
        val scheduled = streamingViewModel.resolveStreamingTrackListForPlayback(
            tracks,
            index,
            listener.adaptiveStreamingQuality()
        ) { resolved ->
            if (resolved == null) {
                listener.setStatus(
                    streamingViewModel.prepareStreamingPlaybackStatusText(listener.languageMode(), null).resolveFailed
                )
                return@resolveStreamingTrackListForPlayback
            }
            listener.applyPlaybackActionResult(
                nowPlayingViewModel.playTrackList(resolved.tracks, resolved.index)
            )
            resolved.tracks.getOrNull(resolved.index)?.let(nowPlayingViewModel::precacheTrack)
        }
        if (scheduled) {
            listener.setStatus(
                streamingViewModel.prepareStreamingPlaybackStatusText(listener.languageMode(), null).resolving
            )
        }
        return scheduled
    }

    fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot?) {
        if (snapshot == null || !snapshot.playing) {
            return
        }
        listener.maybeAppendHeartbeatRecommendations(snapshot)
        streamingViewModel.preResolveNextStreamingTrack(
            snapshot,
            listener.queueSnapshot(),
            listener.adaptiveStreamingQuality()
        ) { oldTrackId, resolved ->
            if (resolved == null) {
                return@preResolveNextStreamingTrack
            }
            nowPlayingViewModel.replaceQueuedTrack(oldTrackId, resolved)
            nowPlayingViewModel.precacheTrack(resolved)
        }
        streamingViewModel.preResolveStreamingQueueWindow(
            snapshot,
            listener.queueSnapshot(),
            listener.adaptiveStreamingQuality()
        ) { oldTrackId, resolved ->
            if (resolved == null) {
                return@preResolveStreamingQueueWindow
            }
            nowPlayingViewModel.replaceQueuedTrack(oldTrackId, resolved)
            nowPlayingViewModel.precacheTrack(resolved)
        }
    }

    fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot?) {
        if (snapshot == null) {
            return
        }
        val recoveryQuality = streamingViewModel.recoverStreamingBuffering(
            snapshot,
            listener.selectedStreamingQuality(),
            listener.adaptiveStreamingQuality()
        ) { resolved ->
            if (resolved == null) {
                return@recoverStreamingBuffering
            }
            nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.track, resolved.positionMs)
            listener.setStatus(
                streamingViewModel.prepareStreamingPlaybackStatusText(
                    listener.languageMode(),
                    resolved.quality
                ).qualityDowngraded
            )
        } ?: return
        listener.setStatus(
            streamingViewModel.prepareStreamingPlaybackStatusText(
                listener.languageMode(),
                recoveryQuality
            ).qualityDowngrading
        )
    }
}
