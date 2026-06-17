package app.yukine

import app.yukine.data.MusicLibraryRepository

enum class SettingsPreferenceKey {
    ThemeMode,
    AccentMode,
    LanguageMode,
    PlaybackSpeed,
    AppVolume,
    StreamingAudioQuality,
    OnlineLyricsEnabled,
    ConcurrentPlaybackEnabled,
    LyricsOffsetMs
}

data class SettingsPreferenceUpdate(
    val key: SettingsPreferenceKey,
    val value: Any
)

internal interface SettingsPreferenceOperations {
    fun saveThemeMode(mode: String)
    fun saveAccentMode(accent: String)
    fun saveLanguageMode(languageMode: String)
    fun savePlaybackSpeed(speed: Float)
    fun saveAppVolume(volume: Float)
    fun saveStreamingAudioQuality(quality: String)
    fun saveOnlineLyricsEnabled(enabled: Boolean)
    fun saveConcurrentPlaybackEnabled(enabled: Boolean)
    fun saveLyricsOffsetMs(offsetMs: Long)
}

internal class MusicLibrarySettingsPreferenceOperations(
    private val repository: MusicLibraryRepository
) : SettingsPreferenceOperations {
    override fun saveThemeMode(mode: String) = repository.saveThemeMode(mode)

    override fun saveAccentMode(accent: String) = repository.saveAccentMode(accent)

    override fun saveLanguageMode(languageMode: String) = repository.saveLanguageMode(languageMode)

    override fun savePlaybackSpeed(speed: Float) = repository.savePlaybackSpeed(speed)

    override fun saveAppVolume(volume: Float) = repository.saveAppVolume(volume)

    override fun saveStreamingAudioQuality(quality: String) =
        repository.saveStreamingAudioQuality(quality)

    override fun saveOnlineLyricsEnabled(enabled: Boolean) =
        repository.saveOnlineLyricsEnabled(enabled)

    override fun saveConcurrentPlaybackEnabled(enabled: Boolean) =
        repository.saveConcurrentPlaybackEnabled(enabled)

    override fun saveLyricsOffsetMs(offsetMs: Long) = repository.saveLyricsOffsetMs(offsetMs)
}

internal class ApplySettingsPreferenceUseCase(
    private val operations: SettingsPreferenceOperations
) {
    fun execute(update: SettingsPreferenceUpdate) {
        when (update.key) {
            SettingsPreferenceKey.ThemeMode -> operations.saveThemeMode(update.value as String)
            SettingsPreferenceKey.AccentMode -> operations.saveAccentMode(update.value as String)
            SettingsPreferenceKey.LanguageMode -> operations.saveLanguageMode(update.value as String)
            SettingsPreferenceKey.PlaybackSpeed -> operations.savePlaybackSpeed(update.value as Float)
            SettingsPreferenceKey.AppVolume -> operations.saveAppVolume(update.value as Float)
            SettingsPreferenceKey.StreamingAudioQuality ->
                operations.saveStreamingAudioQuality(update.value as String)
            SettingsPreferenceKey.OnlineLyricsEnabled ->
                operations.saveOnlineLyricsEnabled(update.value as Boolean)
            SettingsPreferenceKey.ConcurrentPlaybackEnabled ->
                operations.saveConcurrentPlaybackEnabled(update.value as Boolean)
            SettingsPreferenceKey.LyricsOffsetMs -> operations.saveLyricsOffsetMs(update.value as Long)
        }
    }
}
