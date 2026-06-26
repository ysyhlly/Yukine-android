package app.yukine

internal fun interface StatusLanguageModeProvider {
    fun languageMode(): String
}

internal fun interface RawStatusUpdater {
    fun update(message: String)
}
