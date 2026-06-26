package app.yukine

internal class StreamingGatewayEventController(
    private val streamingViewModel: StreamingViewModel,
    private val languageModeProvider: StatusLanguageModeProvider,
    private val renderSelectedTabAction: Runnable,
    private val statusSink: SettingsStatusSink
) : StreamingGatewayController.ViewModelBridge, StreamingGatewayController.Listener {
    override fun configureStreamingRepository() {
        streamingViewModel.configureStreamingRepository()
    }

    override fun refreshStreamingProviders() {
        streamingViewModel.refreshStreamingProviders().invokeOnCompletion {
            renderSelectedTabAction.run()
        }
    }

    override fun onStreamingGatewayApplied(endpoint: String) {
        renderSelectedTabAction.run()
        statusSink.set(AppLanguage.text(languageModeProvider.languageMode(), "streaming.gateway.applied") + endpoint)
    }
}
