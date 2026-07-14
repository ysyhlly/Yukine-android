package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.playback.AudioEffectSettings

internal interface SettingsPreferenceOperations {
    fun saveThemeMode(mode: String)
    fun saveAccentMode(accent: String)
    fun saveLanguageMode(languageMode: String)
    fun savePlaybackSpeed(speed: Float)
    fun saveAppVolume(volume: Float)
    fun saveStreamingAudioQuality(quality: String)
    fun saveRefuseAutomaticQualityDowngrade(refuse: Boolean)
    fun saveOnlineLyricsEnabled(enabled: Boolean)
    fun saveConcurrentPlaybackEnabled(enabled: Boolean)
    fun saveLyricsOffsetMs(offsetMs: Long)
    fun saveAudioEffectSettings(settings: AudioEffectSettings)
    fun saveStatusBarLyricsEnabled(enabled: Boolean)
    fun saveSystemMediaLyricsTitleEnabled(enabled: Boolean)
    fun saveFloatingLyricsEnabled(enabled: Boolean)
    fun saveNowPlayingGesturesEnabled(enabled: Boolean)
    fun savePlaybackRestoreEnabled(enabled: Boolean)
    fun saveReplayGainEnabled(enabled: Boolean)
    fun saveDebugPromptsEnabled(enabled: Boolean)
    fun saveCustomBackgroundBlurEnabled(enabled: Boolean)
    fun saveCustomBackgroundBlurRadiusDp(radiusDp: Float)
    fun saveGlassBlurEnabled(enabled: Boolean)
    fun saveGlassBlurRadiusDp(radiusDp: Float)
    fun saveGlassSurfaceOpacity(opacity: Float)
    fun saveShareStyle(style: String)
    fun savePageBackgrounds(backgrounds: PageBackgrounds)
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

    override fun saveRefuseAutomaticQualityDowngrade(refuse: Boolean) =
        repository.saveRefuseAutomaticQualityDowngrade(refuse)

    override fun saveOnlineLyricsEnabled(enabled: Boolean) =
        repository.saveOnlineLyricsEnabled(enabled)

    override fun saveConcurrentPlaybackEnabled(enabled: Boolean) =
        repository.saveConcurrentPlaybackEnabled(enabled)

    override fun saveLyricsOffsetMs(offsetMs: Long) = repository.saveLyricsOffsetMs(offsetMs)

    override fun saveAudioEffectSettings(settings: AudioEffectSettings) =
        repository.saveAudioEffectSettings(settings)

    override fun saveStatusBarLyricsEnabled(enabled: Boolean) =
        repository.saveStatusBarLyricsEnabled(enabled)

    override fun saveSystemMediaLyricsTitleEnabled(enabled: Boolean) =
        repository.saveSystemMediaLyricsTitleEnabled(enabled)

    override fun saveFloatingLyricsEnabled(enabled: Boolean) =
        repository.saveFloatingLyricsEnabled(enabled)

    override fun saveNowPlayingGesturesEnabled(enabled: Boolean) =
        repository.saveNowPlayingGesturesEnabled(enabled)

    override fun savePlaybackRestoreEnabled(enabled: Boolean) =
        repository.savePlaybackRestoreEnabled(enabled)

    override fun saveReplayGainEnabled(enabled: Boolean) =
        repository.saveReplayGainEnabled(enabled)

    override fun saveDebugPromptsEnabled(enabled: Boolean) =
        repository.saveDebugPromptsEnabled(enabled)

    override fun saveCustomBackgroundBlurEnabled(enabled: Boolean) =
        repository.saveCustomBackgroundBlurEnabled(enabled)

    override fun saveCustomBackgroundBlurRadiusDp(radiusDp: Float) =
        repository.saveCustomBackgroundBlurRadiusDp(radiusDp)

    override fun saveGlassBlurEnabled(enabled: Boolean) = repository.saveGlassBlurEnabled(enabled)

    override fun saveGlassBlurRadiusDp(radiusDp: Float) = repository.saveGlassBlurRadiusDp(radiusDp)
    override fun saveGlassSurfaceOpacity(opacity: Float) = repository.saveGlassSurfaceOpacity(opacity)

    override fun saveShareStyle(style: String) =
        repository.saveShareStyle(style)

    override fun savePageBackgrounds(backgrounds: PageBackgrounds) =
        repository.savePageBackgrounds(backgrounds)
}

internal class ApplySettingsPreferenceUseCase(
    private val operations: SettingsPreferenceOperations
) {
    fun execute(update: SettingsPreferenceUpdate): Boolean {
        return runCatching {
            when (update.key) {
                SettingsPreferenceKey.ThemeMode -> operations.saveThemeMode(update.value as String)
                SettingsPreferenceKey.AccentMode -> operations.saveAccentMode(update.value as String)
                SettingsPreferenceKey.LanguageMode -> operations.saveLanguageMode(update.value as String)
                SettingsPreferenceKey.PlaybackSpeed -> operations.savePlaybackSpeed(update.value as Float)
                SettingsPreferenceKey.AppVolume -> operations.saveAppVolume(update.value as Float)
                SettingsPreferenceKey.StreamingAudioQuality ->
                    operations.saveStreamingAudioQuality(update.value as String)
                SettingsPreferenceKey.RefuseAutomaticQualityDowngrade ->
                    operations.saveRefuseAutomaticQualityDowngrade(update.value as Boolean)
                SettingsPreferenceKey.OnlineLyricsEnabled ->
                    operations.saveOnlineLyricsEnabled(update.value as Boolean)
                SettingsPreferenceKey.ConcurrentPlaybackEnabled ->
                    operations.saveConcurrentPlaybackEnabled(update.value as Boolean)
                SettingsPreferenceKey.LyricsOffsetMs -> operations.saveLyricsOffsetMs(update.value as Long)
                SettingsPreferenceKey.AudioEffectSettings ->
                    operations.saveAudioEffectSettings(update.value as AudioEffectSettings)
                SettingsPreferenceKey.StatusBarLyricsEnabled ->
                    operations.saveStatusBarLyricsEnabled(update.value as Boolean)
                SettingsPreferenceKey.SystemMediaLyricsTitleEnabled ->
                    operations.saveSystemMediaLyricsTitleEnabled(update.value as Boolean)
                SettingsPreferenceKey.FloatingLyricsEnabled ->
                    operations.saveFloatingLyricsEnabled(update.value as Boolean)
                SettingsPreferenceKey.NowPlayingGesturesEnabled ->
                    operations.saveNowPlayingGesturesEnabled(update.value as Boolean)
                SettingsPreferenceKey.PlaybackRestoreEnabled ->
                    operations.savePlaybackRestoreEnabled(update.value as Boolean)
                SettingsPreferenceKey.ReplayGainEnabled ->
                    operations.saveReplayGainEnabled(update.value as Boolean)
                SettingsPreferenceKey.DebugPromptsEnabled ->
                    operations.saveDebugPromptsEnabled(update.value as Boolean)
                SettingsPreferenceKey.CustomBackgroundBlurEnabled ->
                    operations.saveCustomBackgroundBlurEnabled(update.value as Boolean)
                SettingsPreferenceKey.CustomBackgroundBlurRadiusDp ->
                    operations.saveCustomBackgroundBlurRadiusDp(update.value as Float)
                SettingsPreferenceKey.GlassBlurEnabled ->
                    operations.saveGlassBlurEnabled(update.value as Boolean)
                SettingsPreferenceKey.GlassBlurRadiusDp ->
                    operations.saveGlassBlurRadiusDp(update.value as Float)
                SettingsPreferenceKey.GlassSurfaceOpacity ->
                    operations.saveGlassSurfaceOpacity(update.value as Float)
                SettingsPreferenceKey.ShareStyle ->
                    operations.saveShareStyle(update.value as String)
                SettingsPreferenceKey.PageBackgrounds ->
                    operations.savePageBackgrounds(update.value as PageBackgrounds)
            }
        }.isSuccess
    }
}
