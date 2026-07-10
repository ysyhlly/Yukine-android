package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter

internal class StreamingPlaybackController(
    private val streamingViewModel: StreamingViewModel,
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    private companion object {
        private const val PRE_RESOLVE_WINDOW_TARGETS = 3
        private const val PRE_RESOLVE_LOOKAHEAD_LIMIT = 32
    }

    interface Listener {
        fun languageMode(): String

        fun adaptiveStreamingQuality(): StreamingAudioQuality

        fun selectedStreamingQuality(): StreamingAudioQuality

        fun queueSnapshot(): List<Track>

        fun queueSize(): Int

        fun queueTrackAt(index: Int): Track?

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
            resolved.tracks.getOrNull(resolved.index)?.let(nowPlayingViewModel::warmPlaybackTrack)
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
        val queue = boundedPreResolveQueue(snapshot) ?: return
        val preResolveSnapshot = preResolveSnapshot(snapshot, queue)
        val quality = listener.adaptiveStreamingQuality()
        streamingViewModel.preResolveNextStreamingTrack(
            preResolveSnapshot,
            queue,
            quality
        ) { oldTrackId, resolved ->
            if (resolved == null) {
                return@preResolveNextStreamingTrack
            }
            nowPlayingViewModel.replaceQueuedTrack(oldTrackId, resolved)
            nowPlayingViewModel.warmPlaybackTrack(resolved)
        }
        streamingViewModel.preResolveStreamingQueueWindowBatch(
            preResolveSnapshot,
            queue,
            quality
        ) { resolvedTracks ->
            nowPlayingViewModel.replaceQueuedTracks(resolvedTracks)
            resolvedTracks.values.forEach(nowPlayingViewModel::warmPlaybackTrack)
        }
    }

    private fun boundedPreResolveQueue(snapshot: PlaybackStateSnapshot): List<Track>? {
        val queueSize = listener.queueSize()
        val currentIndex = snapshot.currentIndex
        if (queueSize <= 1 || currentIndex !in 0 until queueSize) {
            return null
        }
        val current = snapshot.currentTrack ?: listener.queueTrackAt(currentIndex) ?: return null
        val nextIndex = (currentIndex + 1).floorMod(queueSize)
        if (nextIndex == currentIndex) {
            return null
        }
        val next = listener.queueTrackAt(nextIndex) ?: return null
        val maxWindowSize = 2 + PRE_RESOLVE_WINDOW_TARGETS
        val queue = ArrayList<Track>(minOf(queueSize, maxWindowSize))
        queue += current
        queue += next

        val maxLookahead = minOf(queueSize - 2, PRE_RESOLVE_LOOKAHEAD_LIMIT)
        var offset = 2
        while (offset < 2 + maxLookahead && queue.size < maxWindowSize) {
            val index = (currentIndex + offset).floorMod(queueSize)
            val candidate = listener.queueTrackAt(index)
            if (candidate != null && StreamingPlaybackAdapter.isUnresolvedStreamingTrack(candidate)) {
                queue += candidate
            }
            offset += 1
        }
        return queue
    }

    private fun preResolveSnapshot(
        source: PlaybackStateSnapshot,
        queue: List<Track>
    ): PlaybackStateSnapshot = PlaybackStateSnapshot(
        queue.firstOrNull(),
        0,
        queue.size,
        source.positionMs,
        source.durationMs,
        source.playing,
        source.preparing,
        source.errorMessage,
        source.shuffleEnabled,
        source.repeatMode,
        source.playbackSpeed,
        source.appVolume,
        source.sleepTimerRemainingMs,
        source.waveform,
        source.spectrum,
        source.realtimeBeat
    )

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

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}
