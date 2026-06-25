package app.yukine

internal fun interface MessageLanguageModeProvider {
    fun languageMode(): String
}

internal class MessageTextResolver(
    private val languageModeProvider: MessageLanguageModeProvider
) {
    fun text(key: String?): String {
        val cleanKey = key?.trim().orEmpty()
        if (cleanKey.isEmpty()) {
            return ""
        }
        return AppLanguage.text(languageModeProvider.languageMode(), cleanKey)
    }
}
