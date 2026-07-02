package app.yukine

import java.util.function.Supplier

internal class MessageTextResolver(
    private val languageModeProvider: Supplier<String>
) {
    fun text(key: String?): String {
        val cleanKey = key?.trim().orEmpty()
        if (cleanKey.isEmpty()) {
            return ""
        }
        return AppLanguage.text(languageModeProvider.get(), cleanKey)
    }
}
