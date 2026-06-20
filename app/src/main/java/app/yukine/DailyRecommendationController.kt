package app.yukine

import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal class DailyRecommendationController(
    private val streamingViewModel: StreamingViewModel,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    fun interface LanguageProvider {
        fun languageMode(): String
    }

    interface Listener {
        fun playRecommendationTracks(
            streamingTracks: List<StreamingTrack>,
            emptyStatus: String,
            title: String
        )

        fun setStatus(status: String)
    }

    fun playStreamingDailyRecommendations(provider: StreamingProviderName?) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingDailyRecommendationRequest(provider, languageMode)
        if (request == null) {
            listener.setStatus(streamingViewModel.streamingDailyRecommendationEmptyStatus(languageMode))
            return
        }
        listener.setStatus(request.loadingStatus)
        streamingViewModel.fetchDailyRecommendations(request.provider) { streamingTracks ->
            listener.playRecommendationTracks(
                streamingTracks,
                request.emptyStatus,
                request.title
            )
        }
    }
}
