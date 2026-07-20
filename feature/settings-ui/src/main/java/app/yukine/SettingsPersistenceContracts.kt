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
    LyricsOffsetMs,
    AudioEffectSettings,
    StatusBarLyricsEnabled,
    SystemMediaLyricsTitleEnabled,
    FloatingLyricsEnabled,
    NowPlayingGesturesEnabled,
    PlaybackRestoreEnabled,
    ReplayGainEnabled,
    AudioExclusiveEnabled,
    DebugPromptsEnabled,
    CustomBackgroundBlurEnabled,
    CustomBackgroundBlurRadiusDp,
    GlassBlurEnabled,
    GlassBlurRadiusDp,
    GlassSurfaceOpacity,
    CompactSettingsCards,
    HomeDashboardLayout,
    ShareStyle,
    PageBackgrounds,
    BitPerfectEnabled,
    UsbExclusiveEnabled
}

data class SettingsPreferenceUpdate(
    val key: SettingsPreferenceKey,
    val value: Any
)
