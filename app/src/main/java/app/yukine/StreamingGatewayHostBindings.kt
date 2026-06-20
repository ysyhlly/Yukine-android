package app.yukine

internal class StreamingGatewayHostBindings(
    private val languageModeProvider: StatusLanguageModeProvider,
    private val renderSelectedTabAction: Runnable,
    private val statusSink: SettingsStatusSink
) : StreamingGatewayEventController.Host {
    override fun languageMode(): String {
        return languageModeProvider.languageMode()
    }

    override fun renderSelectedTab() {
        renderSelectedTabAction.run()
    }

    override fun setStatus(message: String) {
        statusSink.set(message)
    }
}
