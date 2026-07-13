package app.yukine

import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal data class StreamingDailyRecommendationResult(
    val tracks: List<StreamingTrack>,
    val diagnostics: StreamingGatewayDiagnostics
)

internal class StreamingDailyRecommendationUseCase(
    private val repositorySource: StreamingRepositorySource
) {
    suspend fun fetch(provider: StreamingProviderName): StreamingDailyRecommendationResult {
        val repository = repositorySource.current()
        return StreamingDailyRecommendationResult(
            tracks = repository.dailyRecommendations(provider),
            diagnostics = repository.diagnostics()
        )
    }
}
