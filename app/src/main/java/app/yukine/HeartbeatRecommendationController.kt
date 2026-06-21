package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName

internal class HeartbeatRecommendationController(
    private val streamingViewModel: StreamingViewModel,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    fun interface LanguageProvider {
        fun languageMode(): String
    }

    interface Listener {
        fun hasPlaybackService(): Boolean

        fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest

        fun stopHeartbeatRecommendationMode()

        fun appendToQueue(presentation: StreamingRecommendationPresentation)

        fun playHeartbeatRecommendationTracks(
            streamingTracks: List<app.yukine.streaming.StreamingTrack>,
            emptyStatus: String,
            playingStatus: String
        )

        fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)

        fun setStatus(status: String)
    }

    fun playStreamingHeartbeatRecommendations(provider: StreamingProviderName?) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)
        if (request == null) {
            listener.setStatus(streamingViewModel.streamingHeartbeatRecommendationEmptyStatus(languageMode))
            return
        }
        listener.setStatus(request.loadingStatus)
        val seedRequest = listener.seedRequest(request.provider)
        if (seedRequest.hasSeed) {
            fetchHeartbeatRecommendations(request, seedRequest.seedTrackId, seedRequest.playlistId)
            return
        }
        resolveHeartbeatSeedFromQueue(request, seedRequest)
    }

    fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot?) {
        if (!listener.hasPlaybackService()) {
            return
        }
        val languageMode = languageProvider.languageMode()
        val refill = streamingViewModel.prepareHeartbeatRecommendationRefill(snapshot) ?: return
        val provider = refill.provider
        val request = listener.seedRequest(provider)
        if (!request.hasSeed) {
            listener.stopHeartbeatRecommendationMode()
            return
        }
        streamingViewModel.fetchHeartbeatRecommendations(provider, request.seedTrackId, request.playlistId) { streamingTracks ->
            if (!streamingViewModel.acceptsHeartbeatRecommendationRefill(provider) || !listener.hasPlaybackService()) {
                streamingViewModel.markHeartbeatRecommendationRefillFinished(provider)
                return@fetchHeartbeatRecommendations
            }
            val presentation = streamingViewModel.prepareHeartbeatRecommendationAppendPresentation(
                streamingTracks,
                languageMode
            )
            if (presentation.empty) {
                return@fetchHeartbeatRecommendations
            }
            listener.appendToQueue(presentation)
            listener.setStatus(presentation.readyStatus)
        }
    }

    private fun fetchHeartbeatRecommendations(
        request: StreamingHeartbeatRecommendationRequest,
        seedTrackId: String,
        playlistId: String
    ) {
        streamingViewModel.fetchHeartbeatRecommendations(request.provider, seedTrackId, playlistId) { streamingTracks ->
            listener.playHeartbeatRecommendationTracks(
                streamingTracks,
                request.emptyStatus,
                request.playingStatus
            )
        }
    }

    private fun resolveHeartbeatSeedFromQueue(
        recommendationRequest: StreamingHeartbeatRecommendationRequest,
        seedRequest: HeartbeatRecommendationSeedRequest
    ) {
        if (!seedRequest.hasCandidates) {
            listener.logSeedMiss(seedRequest)
            streamingViewModel.markHeartbeatRecommendationLoadingFinished()
            listener.setStatus(recommendationRequest.emptyStatus)
            return
        }
        streamingViewModel.resolveHeartbeatRecommendationSeed(
            recommendationRequest.provider,
            seedRequest.candidates
        ) { resolvedTrackId ->
            if (!streamingViewModel.canContinueHeartbeatRecommendationLoading(recommendationRequest.provider)) {
                return@resolveHeartbeatRecommendationSeed
            }
            if (!resolvedTrackId.isNullOrEmpty()) {
                fetchHeartbeatRecommendations(recommendationRequest, resolvedTrackId, resolvedTrackId)
                return@resolveHeartbeatRecommendationSeed
            }
            listener.logSeedMiss(seedRequest)
            streamingViewModel.markHeartbeatRecommendationLoadingFinished()
            listener.setStatus(recommendationRequest.emptyStatus)
        }
    }
}
