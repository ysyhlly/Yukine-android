package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.playback.AudioEffectSettings

internal data class LoadedSettingsPreferences(
    val themeMode: String,
    val accentMode: String,
    val languageMode: String,
    val playbackSpeed: Float,
    val appVolume: Float,
    val streamingAudioQuality: String,
    val concurrentPlaybackEnabled: Boolean,
    val audioEffectSettings: AudioEffectSettings,
    val statusBarLyricsEnabled: Boolean,
    val floatingLyricsEnabled: Boolean,
    val nowPlayingGesturesEnabled: Boolean,
    val playbackRestoreEnabled: Boolean,
    val replayGainEnabled: Boolean
)

internal interface SettingsPreferenceLoadOperations {
    fun loadThemeMode(): String
    fun loadAccentMode(): String
    fun loadLanguageMode(): String
    fun loadPlaybackSpeed(): Float
    fun loadAppVolume(): Float
    fun loadStreamingAudioQuality(): String
    fun loadConcurrentPlaybackEnabled(): Boolean
    fun loadAudioEffectSettings(): AudioEffectSettings
    fun loadStatusBarLyricsEnabled(): Boolean
    fun loadFloatingLyricsEnabled(): Boolean
    fun loadNowPlayingGesturesEnabled(): Boolean
    fun loadPlaybackRestoreEnabled(): Boolean
    fun loadReplayGainEnabled(): Boolean
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

    override fun loadAudioEffectSettings(): AudioEffectSettings =
        repository.loadAudioEffectSettings()

    override fun loadStatusBarLyricsEnabled(): Boolean =
        repository.loadStatusBarLyricsEnabled()

    override fun loadFloatingLyricsEnabled(): Boolean =
        repository.loadFloatingLyricsEnabled()

    override fun loadNowPlayingGesturesEnabled(): Boolean =
        repository.loadNowPlayingGesturesEnabled()

    override fun loadPlaybackRestoreEnabled(): Boolean =
        repository.loadPlaybackRestoreEnabled()

    override fun loadReplayGainEnabled(): Boolean =
        repository.loadReplayGainEnabled()
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
            concurrentPlaybackEnabled = operations.loadConcurrentPlaybackEnabled(),
            audioEffectSettings = operations.loadAudioEffectSettings(),
            statusBarLyricsEnabled = operations.loadStatusBarLyricsEnabled(),
            floatingLyricsEnabled = operations.loadFloatingLyricsEnabled(),
            nowPlayingGesturesEnabled = operations.loadNowPlayingGesturesEnabled(),
            playbackRestoreEnabled = operations.loadPlaybackRestoreEnabled(),
            replayGainEnabled = operations.loadReplayGainEnabled()
        )
}

private object EchoThemeModeNormalizer {
    fun themeMode(mode: String): String = app.yukine.ui.EchoTheme.normalizeMode(mode)

    fun accentMode(accent: String): String = app.yukine.ui.EchoTheme.normalizeAccent(accent)
}
