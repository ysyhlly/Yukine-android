package app.yukine

internal fun interface StatusLanguageModeProvider {
    fun languageMode(): String
}

internal fun interface RawStatusUpdater {
    fun update(message: String)
}

internal class StatusMessageHostBindings(
    private val languageModeProvider: StatusLanguageModeProvider,
    private val rawStatusUpdater: RawStatusUpdater
) : StatusMessageController.Host {
    override fun languageMode(): String {
        return languageModeProvider.languageMode()
    }

    override fun updateStatus(message: String) {
        rawStatusUpdater.update(message)
    }
}
