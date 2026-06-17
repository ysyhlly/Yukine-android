package app.yukine

internal class StreamingGatewayEventController(
    private val viewModel: MainActivityViewModel,
    private val host: Host
) : StreamingGatewayController.ViewModelBridge, StreamingGatewayController.Listener {
    interface Host {
        fun languageMode(): String

        fun renderSelectedTab()

        fun setStatus(message: String)
    }

    override fun configureStreamingRepository() {
        viewModel.configureStreamingRepository()
    }

    override fun refreshStreamingProviders() {
        viewModel.refreshStreamingProviders()
    }

    override fun onStreamingGatewayApplied(endpoint: String) {
        host.renderSelectedTab()
        host.setStatus(AppLanguage.text(host.languageMode(), "streaming.gateway.applied") + endpoint)
    }
}
