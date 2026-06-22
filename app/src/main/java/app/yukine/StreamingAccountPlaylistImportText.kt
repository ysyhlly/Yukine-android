package app.yukine

internal object StreamingAccountPlaylistImportText {
    @JvmStatic
    fun noAccountPlaylists(languageMode: String): String =
        AppLanguage.text(languageMode, "streaming.no.account.playlists")

    @JvmStatic
    fun title(languageMode: String): String =
        AppLanguage.text(languageMode, "streaming.account.playlists.import.title")

    @JvmStatic
    fun message(languageMode: String): String =
        AppLanguage.text(languageMode, "streaming.account.playlists.import.message")

    @JvmStatic
    fun confirm(languageMode: String): String =
        AppLanguage.text(languageMode, "streaming.account.playlists.import.confirm")

    @JvmStatic
    fun trackCountSuffix(languageMode: String): String =
        AppLanguage.text(languageMode, "streaming.track.count.suffix")
}
