package app.echo.next

import app.echo.next.data.MusicLibraryRepository

internal data class LoadedSettingsPreferences(
    val themeMode: String,
    val accentMode: String,
    val languageMode: String,
    val playbackSpeed: Float,
    val appVolume: Float,
    val streamingAudioQuality: String,
    val concurrentPlaybackEnabled: Boolean
)

internal interface SettingsPreferenceLoadOperations {
    fun loadThemeMode(): String
    fun loadAccentMode(): String
    fun loadLanguageMode(): String
    fun loadPlaybackSpeed(): Float
    fun loadAppVolume(): Float
    fun loadStreamingAudioQuality(): String
    fun loadConcurrentPlaybackEnabled(): Boolean
}

internal class MusicLibrarySettingsPreferenceLoadOperations(
    private val repository: MusicLibraryRepository
) : SettingsPreferenceLoadOperations {
    override fun loadThemeMode(): String = repository.loadThemeMode()

    override fun loadAccentMode(): String = repository.loadAccentMode()

    override fun loadLanguageMode(): String = repository.loadLanguageMode()

    override fun loadPlaybackSpeed(): Float = repository.loadPlaybackSpeed()

    override fun loadAppVolume(): Float = repository.loadAppVolume()

    override fun loadStreamingAudioQuality(): String = repository.loadStreamingAudioQuality()

    override fun loadConcurrentPlaybackEnabled(): Boolean =
        repository.loadConcurrentPlaybackEnabled()
}

internal class LoadSettingsPreferencesUseCase(
    private val operations: SettingsPreferenceLoadOperations
) {
    fun execute(): LoadedSettingsPreferences =
        LoadedSettingsPreferences(
            themeMode = EchoThemeModeNormalizer.themeMode(operations.loadThemeMode()),
            accentMode = EchoThemeModeNormalizer.accentMode(operations.loadAccentMode()),
            languageMode = AppLanguage.normalizeMode(operations.loadLanguageMode()),
            playbackSpeed = operations.loadPlaybackSpeed(),
            appVolume = operations.loadAppVolume(),
            streamingAudioQuality = StreamingQualityPreference.normalize(operations.loadStreamingAudioQuality()),
            concurrentPlaybackEnabled = operations.loadConcurrentPlaybackEnabled()
        )
}

private object EchoThemeModeNormalizer {
    fun themeMode(mode: String): String = app.echo.next.ui.EchoTheme.normalizeMode(mode)

    fun accentMode(accent: String): String = app.echo.next.ui.EchoTheme.normalizeAccent(accent)
}
