package app.yukine

enum class SettingsPreferenceKey {
    ThemeMode,
    AccentMode,
    LanguageMode,
    PlaybackSpeed,
    AppVolume,
    StreamingAudioQuality,
    RefuseAutomaticQualityDowngrade,
    OnlineLyricsEnabled,
    ConcurrentPlaybackEnabled,
    LyricsOffsetMs,
    AudioEffectSettings,
    StatusBarLyricsEnabled,
    SystemMediaLyricsTitleEnabled,
    FloatingLyricsEnabled,
    NowPlayingGesturesEnabled,
    PlaybackRestoreEnabled,
    ReplayGainEnabled,
    DebugPromptsEnabled,
    CustomBackgroundBlurEnabled,
    CustomBackgroundBlurRadiusDp,
    GlassBlurEnabled,
    GlassBlurRadiusDp,
    GlassSurfaceOpacity,
    CompactSettingsCards,
    ShareStyle,
    PageBackgrounds
}

data class SettingsPreferenceUpdate(
    val key: SettingsPreferenceKey,
    val value: Any
)
