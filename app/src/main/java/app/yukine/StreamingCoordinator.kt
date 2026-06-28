package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingTrack

internal class StreamingCoordinator(
    private val streamingViewModel: StreamingViewModel,
    private val streamingRecommendationViewModel: StreamingRecommendationViewModel,
    val streamingPlaybackController: StreamingPlaybackController,
    val streamingAuthCallbackController: StreamingAuthCallbackController,
    val streamingSearchActionHandler: StreamingSearchActionHandler,
    val streamingSearchRenderController: StreamingSearchRenderController,
    private val settingsStore: MainSettingsStore,
    private val playbackStore: MainPlaybackStore,
    private val statusMessageController: StatusMessageController,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore,
    private val resolveStreamingPlaybackUseCase: ResolveStreamingPlaybackUseCase,
    private val listener: Listener
) {

    interface Listener {
        fun playTrackListFromHost(tracks: List<Track>, index: Int)
        fun renderSelectedTab()
        fun renderNowBar()
        fun currentPlaybackService(): app.yukine.playback.EchoPlaybackService?
    }

    var heartbeatSeedBinder: HeartbeatRecommendationSeedBinder? = null
    var heartbeatRecommendationController: HeartbeatRecommendationController? = null
    var recommendationActionCallbacks: RecommendationActionCallbacks? = null

    private var unifiedStreamingPlaybackRequestId = 0

    fun refreshStreamingProviders(): kotlinx.coroutines.Job {
        val job = streamingViewModel.refreshStreamingProviders()
        job.invokeOnCompletion {
            syncStreamingProvidersAndRender()
        }
        return job
    }

    fun applyStreamingGatewayEndpoint(endpoint: String): kotlinx.coroutines.Job {
        streamingGatewaySettingsStore.setEndpoint(endpoint)
        streamingViewModel.configureStreamingRepository()
        val refreshJob = refreshStreamingProviders()
        listener.renderSelectedTab()
        statusMessageController.setStatus(
            AppLanguage.text(languageMode(), "streaming.gateway.applied")
                    + streamingGatewaySettingsStore.endpoint()
        )
        return refreshJob
    }

    fun playUnifiedStreamingTrack(track: StreamingTrack?) {
        if (track == null) return
        if (!track.playable) {
            val reason = track.unavailableReason
            statusMessageController.showFeedback(
                if (reason.isNullOrBlank()) "该在线歌曲暂不可播放" else reason
            )
            return
        }
        val requestId = ++unifiedStreamingPlaybackRequestId
        statusMessageController.showFeedback("正在解析在线歌曲：" + track.title)
        streamingViewModel.resolveStreamingTrackForPlayback(
            track.provider,
            track.providerTrackId,
            track,
            selectedStreamingQuality()
        ) { resolved ->
            if (requestId != unifiedStreamingPlaybackRequestId) return@resolveStreamingTrackForPlayback
            if (resolved == null) {
                statusMessageController.showFeedback("在线歌曲解析失败，请稍后再试")
                return@resolveStreamingTrackForPlayback
            }
            listener.playTrackListFromHost(listOf(resolved), 0)
            statusMessageController.showFeedback("开始播放：" + resolved.title)
        }
    }

    fun switchNowPlayingSource(effect: NowPlayingEffect.SwitchSource, nowPlayingViewModel: NowPlayingViewModel) {
        val current = effect.track ?: run {
            statusMessageController.showFeedback("当前没有可切换的歌曲")
            return
        }
        val quality = effect.quality ?: selectedStreamingQuality()
        statusMessageController.showFeedback("正在切换音源：" + effect.provider.wireName)
        streamingViewModel.resolveStreamingTrackForPlayback(
            effect.provider,
            effect.providerTrackId,
            resolveStreamingPlaybackUseCase.metadataFor(current, effect.provider, effect.providerTrackId),
            quality
        ) { resolved ->
            if (resolved == null) {
                statusMessageController.showFeedback("音源切换失败，请换一个来源再试")
                return@resolveStreamingTrackForPlayback
            }
            val positionMs = playbackStore.snapshot()?.positionMs ?: 0L
            nowPlayingViewModel.replaceCurrentTrackAndResume(resolved, positionMs)
            statusMessageController.showFeedback("已切换音源：" + resolved.title)
            playbackStore.publish(listener.currentPlaybackService())
            listener.renderNowBar()
            listener.renderSelectedTab()
        }
    }

    fun cancelUnifiedStreamingRequest() {
        unifiedStreamingPlaybackRequestId++
    }

    fun syncStreamingProvidersAndRender() {
        streamingRecommendationViewModel.updateProviders(streamingViewModel.state.providers)
        listener.renderSelectedTab()
    }

    fun selectedStreamingQuality(): StreamingAudioQuality {
        val selected = settingsStore.streamingAudioQuality()
            ?: StreamingQualityPreference.defaultValue()
        return StreamingQualityPreference.ceilingFor(selected)
    }

    fun handleInitialIntent(intent: android.content.Intent?) {
        streamingAuthCallbackController.handleInitialIntent(intent)
    }

    fun handleNewIntent(intent: android.content.Intent?) {
        streamingAuthCallbackController.handleNewIntent(intent)
    }

    private fun languageMode(): String =
        settingsStore.languageMode() ?: AppLanguage.MODE_SYSTEM
}
