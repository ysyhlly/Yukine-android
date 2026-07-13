package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackReadModel
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal fun interface StreamingSourceResolver {
    fun resolve(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?,
        quality: StreamingAudioQuality,
        onResolved: StreamingCallback<Track?>
    )
}

/**
 * Owns source-switch validation, streaming resolution, stale-request rejection and resume policy.
 * The Activity only dispatches typed effects to this owner.
 */
internal class NowPlayingSourceSwitchOwner(
    private val planner: StreamingSourceSwitchPlanner,
    private val resolver: StreamingSourceResolver,
    private val playbackReadModel: PlaybackReadModel,
    private val quality: StreamingPlaybackQuality,
    private val isLatestRequest: (Long) -> Boolean,
    private val replaceCurrentSourceAndResume: (Long, Track, Long) -> Unit,
    private val feedback: (String) -> Unit
) {
    constructor(
        planner: StreamingSourceSwitchPlanner,
        streamingViewModel: StreamingViewModel,
        nowPlayingViewModel: NowPlayingViewModel,
        playbackReadModel: PlaybackReadModel,
        quality: StreamingPlaybackQuality,
        statusMessageController: StatusMessageController
    ) : this(
        planner = planner,
        resolver = StreamingSourceResolver { provider, providerTrackId, metadata, selectedQuality, callback ->
            streamingViewModel.playbackResolution.resolveStreamingTrackForPlayback(
                provider,
                providerTrackId,
                metadata,
                selectedQuality,
                callback
            )
        },
        playbackReadModel = playbackReadModel,
        quality = quality,
        isLatestRequest = nowPlayingViewModel::isLatestSourceSwitchRequest,
        replaceCurrentSourceAndResume = nowPlayingViewModel::replaceCurrentSourceAndResume,
        feedback = statusMessageController::showFeedback
    )

    fun handle(effect: NowPlayingEffect) {
        when (effect) {
            is NowPlayingEffect.SwitchSource -> switchStreamingSource(effect)
            is NowPlayingEffect.SwitchLibrarySource -> switchLibrarySource(effect)
            else -> Unit
        }
    }

    private fun switchStreamingSource(effect: NowPlayingEffect.SwitchSource) {
        if (!isLatestRequest(effect.requestId)) {
            return
        }
        val positionMs = playbackReadModel.state.value.positionMs
        feedback("正在切换音源：${effect.provider.wireName}")
        resolver.resolve(
            effect.provider,
            effect.providerTrackId,
            planner.metadataFor(effect.track, effect.provider, effect.providerTrackId),
            effect.quality ?: quality.selected()
        ) { resolved ->
            complete(effect.requestId, effect.track, resolved, positionMs)
        }
    }

    private fun switchLibrarySource(effect: NowPlayingEffect.SwitchLibrarySource) {
        if (!isLatestRequest(effect.requestId)) {
            return
        }
        val positionMs = playbackReadModel.state.value.positionMs
        val request = planner.prepareSourceSwitch(effect.replacement)
        if (request == null) {
            complete(effect.requestId, effect.current, effect.replacement, positionMs)
            return
        }
        val provider = request.provider
        val providerTrackId = request.providerTrackId.trim()
        if (provider == null || providerTrackId.isEmpty()) {
            feedback("音源切换暂不可用")
            return
        }
        feedback("正在切换音源：${provider.wireName}")
        resolver.resolve(
            provider,
            providerTrackId,
            planner.metadataFor(effect.replacement, provider, providerTrackId),
            quality.selected()
        ) { resolved ->
            complete(effect.requestId, effect.current, resolved, positionMs)
        }
    }

    private fun complete(requestId: Long, current: Track, replacement: Track?, positionMs: Long) {
        if (!isLatestRequest(requestId)) {
            return
        }
        if (replacement == null) {
            feedback("音源切换失败，请换一个来源再试")
            return
        }
        replaceCurrentSourceAndResume(current.id, replacement, positionMs)
        feedback("已切换音源：${replacement.title}")
    }
}
