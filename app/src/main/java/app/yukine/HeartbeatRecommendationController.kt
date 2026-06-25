package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName

internal class HeartbeatRecommendationController(
    private val recommendationPlayer: HeartbeatRecommendationPlayer,
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

        fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)

        fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)

        fun setStatus(status: String)
    }

    fun playStreamingHeartbeatRecommendations(provider: StreamingProviderName?) {
        val languageMode = languageProvider.languageMode()
        val request = recommendationPlayer.prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)
        if (request == null) {
            listener.setStatus(recommendationPlayer.streamingHeartbeatRecommendationEmptyStatus(languageMode))
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
        val refill = recommendationPlayer.prepareHeartbeatRecommendationRefill(snapshot) ?: return
        val provider = refill.provider
        val request = listener.seedRequest(provider)
        if (!request.hasSeed) {
            listener.stopHeartbeatRecommendationMode()
            return
        }
        recommendationPlayer.fetchHeartbeatRecommendations(provider, request.seedTrackId, request.playlistId) { streamingTracks ->
            if (!recommendationPlayer.acceptsHeartbeatRecommendationRefill(provider) || !listener.hasPlaybackService()) {
                recommendationPlayer.markHeartbeatRecommendationRefillFinished(provider)
                return@fetchHeartbeatRecommendations
            }
            val presentation = recommendationPlayer.prepareHeartbeatRecommendationAppendPresentation(
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
        recommendationPlayer.fetchHeartbeatRecommendations(request.provider, seedTrackId, playlistId) { streamingTracks ->
            val presentation = recommendationPlayer.prepareHeartbeatRecommendationPresentation(
                streamingTracks,
                request.emptyStatus,
                request.playingStatus
            )
            listener.playHeartbeatRecommendation(presentation)
        }
    }

    private fun resolveHeartbeatSeedFromQueue(
        recommendationRequest: StreamingHeartbeatRecommendationRequest,
        seedRequest: HeartbeatRecommendationSeedRequest
    ) {
        if (!seedRequest.hasCandidates) {
            listener.logSeedMiss(seedRequest)
            recommendationPlayer.markHeartbeatRecommendationLoadingFinished()
            listener.setStatus(recommendationRequest.emptyStatus)
            return
        }
        recommendationPlayer.resolveHeartbeatRecommendationSeed(
            recommendationRequest.provider,
            seedRequest.candidates
        ) { resolvedTrackId ->
            if (!recommendationPlayer.canContinueHeartbeatRecommendationLoading(recommendationRequest.provider)) {
                return@resolveHeartbeatRecommendationSeed
            }
            if (!resolvedTrackId.isNullOrEmpty()) {
                fetchHeartbeatRecommendations(recommendationRequest, resolvedTrackId, resolvedTrackId)
                return@resolveHeartbeatRecommendationSeed
            }
            listener.logSeedMiss(seedRequest)
            recommendationPlayer.markHeartbeatRecommendationLoadingFinished()
            listener.setStatus(recommendationRequest.emptyStatus)
        }
    }
}
