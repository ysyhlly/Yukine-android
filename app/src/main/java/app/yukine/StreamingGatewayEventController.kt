package app.yukine

internal class StreamingGatewayEventController(
    private val streamingViewModel: StreamingViewModel,
    private val host: Host
) : StreamingGatewayController.ViewModelBridge, StreamingGatewayController.Listener {
    interface Host {
        fun languageMode(): String

        fun renderSelectedTab()

        fun setStatus(message: String)
    }

    override fun configureStreamingRepository() {
        streamingViewModel.configureStreamingRepository()
    }

    override fun refreshStreamingProviders() {
        streamingViewModel.refreshStreamingProviders().invokeOnCompletion {
            host.renderSelectedTab()
        }
    }

    override fun onStreamingGatewayApplied(endpoint: String) {
        host.renderSelectedTab()
        host.setStatus(AppLanguage.text(host.languageMode(), "streaming.gateway.applied") + endpoint)
    }
}
