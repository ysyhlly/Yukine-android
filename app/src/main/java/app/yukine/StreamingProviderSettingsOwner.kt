package app.yukine

import kotlinx.coroutines.Job
import java.util.function.Consumer
import java.util.function.Supplier

/** Owns gateway changes and keeps all streaming provider consumers synchronized. */
internal class StreamingProviderSettingsOwner(
    private val endpointStore: StreamingGatewayEndpointStore,
    private val streamingViewModel: StreamingViewModel,
    private val recommendationViewModel: StreamingRecommendationViewModel,
    private val languageMode: Supplier<String>,
    private val statusSink: Consumer<String>
) {
    fun configureAndRefresh(): Job {
        streamingViewModel.configureStreamingRepository()
        return refresh()
    }

    fun refresh(): Job {
        val job = streamingViewModel.auth.refreshProviders()
        job.invokeOnCompletion {
            recommendationViewModel.updateProviders(streamingViewModel.state.providers)
        }
        return job
    }

    fun applyEndpoint(endpoint: String): Job {
        endpointStore.setEndpoint(endpoint)
        val refreshJob = configureAndRefresh()
        statusSink.accept(
            AppLanguage.text(languageMode.get(), "streaming.gateway.applied") + endpointStore.endpoint()
        )
        return refreshJob
    }
}
