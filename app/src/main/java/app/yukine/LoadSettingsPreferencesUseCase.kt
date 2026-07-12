package app.yukine
import app.yukine.streaming.StreamingQualityPreference

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
    val systemMediaLyricsTitleEnabled: Boolean,
    val floatingLyricsEnabled: Boolean,
    val nowPlayingGesturesEnabled: Boolean,
    val playbackRestoreEnabled: Boolean,
    val replayGainEnabled: Boolean,
    val debugPromptsEnabled: Boolean,
    val customBackgroundBlurEnabled: Boolean,
    val customBackgroundBlurRadiusDp: Float,
    val glassBlurEnabled: Boolean,
    val glassBlurRadiusDp: Float,
    val glassSurfaceOpacity: Float,
    val shareStyle: String,
    val pageBackgrounds: PageBackgrounds
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
    fun loadSystemMediaLyricsTitleEnabled(): Boolean
    fun loadFloatingLyricsEnabled(): Boolean
    fun loadNowPlayingGesturesEnabled(): Boolean
    fun loadPlaybackRestoreEnabled(): Boolean
    fun loadReplayGainEnabled(): Boolean
    fun loadDebugPromptsEnabled(): Boolean
    fun loadCustomBackgroundBlurEnabled(): Boolean
    fun loadCustomBackgroundBlurRadiusDp(): Float
    fun loadGlassBlurEnabled(): Boolean
    fun loadGlassBlurRadiusDp(): Float
    fun loadGlassSurfaceOpacity(): Float
    fun loadShareStyle(): String
    fun loadPageBackgrounds(): PageBackgrounds
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

    override fun loadSystemMediaLyricsTitleEnabled(): Boolean =
        repository.loadSystemMediaLyricsTitleEnabled()

    override fun loadFloatingLyricsEnabled(): Boolean =
        repository.loadFloatingLyricsEnabled()

    override fun loadNowPlayingGesturesEnabled(): Boolean =
        repository.loadNowPlayingGesturesEnabled()

    override fun loadPlaybackRestoreEnabled(): Boolean =
        repository.loadPlaybackRestoreEnabled()

    override fun loadReplayGainEnabled(): Boolean =
        repository.loadReplayGainEnabled()

    override fun loadDebugPromptsEnabled(): Boolean =
        repository.loadDebugPromptsEnabled()

    override fun loadCustomBackgroundBlurEnabled(): Boolean =
        repository.loadCustomBackgroundBlurEnabled()

    override fun loadCustomBackgroundBlurRadiusDp(): Float =
        repository.loadCustomBackgroundBlurRadiusDp()

    override fun loadGlassBlurEnabled(): Boolean = repository.loadGlassBlurEnabled()

    override fun loadGlassBlurRadiusDp(): Float = repository.loadGlassBlurRadiusDp()
    override fun loadGlassSurfaceOpacity(): Float = repository.loadGlassSurfaceOpacity()

    override fun loadShareStyle(): String = repository.loadShareStyle()

    override fun loadPageBackgrounds(): PageBackgrounds = repository.loadPageBackgrounds()
}

internal class LoadSettingsPreferencesUseCase(
    private val operations: SettingsPreferenceLoadOperations
) {
    fun execute(): LoadedSettingsPreferences {
        val themeMode = EchoThemeModeNormalizer.themeMode(operations.loadThemeMode())
        val accentMode = EchoThemeModeNormalizer.accentMode(operations.loadAccentMode())
        val languageMode = AppLanguage.normalizeMode(operations.loadLanguageMode())
        val playbackSpeed = operations.loadPlaybackSpeed()
        val appVolume = operations.loadAppVolume()
        val streamingAudioQuality = StreamingQualityPreference.normalize(operations.loadStreamingAudioQuality())
        val concurrentPlaybackEnabled = operations.loadConcurrentPlaybackEnabled()
        val audioEffectSettings = operations.loadAudioEffectSettings()
        val statusBarLyricsEnabled = operations.loadStatusBarLyricsEnabled()
        val floatingLyricsEnabled = operations.loadFloatingLyricsEnabled()
        return LoadedSettingsPreferences(
            themeMode = themeMode,
            accentMode = accentMode,
            languageMode = languageMode,
            playbackSpeed = playbackSpeed,
            appVolume = appVolume,
            streamingAudioQuality = streamingAudioQuality,
            concurrentPlaybackEnabled = concurrentPlaybackEnabled,
            audioEffectSettings = audioEffectSettings,
            statusBarLyricsEnabled = statusBarLyricsEnabled && !floatingLyricsEnabled,
            systemMediaLyricsTitleEnabled = operations.loadSystemMediaLyricsTitleEnabled(),
            floatingLyricsEnabled = floatingLyricsEnabled,
            nowPlayingGesturesEnabled = operations.loadNowPlayingGesturesEnabled(),
            playbackRestoreEnabled = operations.loadPlaybackRestoreEnabled(),
            replayGainEnabled = operations.loadReplayGainEnabled(),
            debugPromptsEnabled = operations.loadDebugPromptsEnabled(),
            customBackgroundBlurEnabled = operations.loadCustomBackgroundBlurEnabled(),
            customBackgroundBlurRadiusDp = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(
                operations.loadCustomBackgroundBlurRadiusDp()
            ),
            glassBlurEnabled = operations.loadGlassBlurEnabled(),
            glassBlurRadiusDp = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(operations.loadGlassBlurRadiusDp()),
            glassSurfaceOpacity = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(operations.loadGlassSurfaceOpacity()),
            shareStyle = TrackShareStyle.normalize(operations.loadShareStyle()),
            pageBackgrounds = operations.loadPageBackgrounds()
        )
    }
}

private object EchoThemeModeNormalizer {
    fun themeMode(mode: String): String = app.yukine.ui.EchoTheme.normalizeMode(mode)

    fun accentMode(accent: String): String = app.yukine.ui.EchoTheme.normalizeAccent(accent)
}
