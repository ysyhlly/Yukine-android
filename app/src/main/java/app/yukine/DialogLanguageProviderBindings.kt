package app.yukine

internal fun interface DialogLanguageModeProvider {
    fun languageMode(): String
}

internal class DialogLanguageProviderBindings(
    private val provider: DialogLanguageModeProvider
) : NetworkDialogController.LanguageProvider,
    PlaylistDialogController.LanguageProvider,
    ConfirmationDialogController.LanguageProvider {
    override fun languageMode(): String =
        provider.languageMode()
}
