package app.yukine

import app.yukine.playback.AudioEffectSettings

sealed interface SettingsRuntimeEffect {
    data object ApplyThemeSurface : SettingsRuntimeEffect
    data class RefreshCustomBackgroundAccent(val backgrounds: PageBackgrounds) : SettingsRuntimeEffect
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsRuntimeEffect
    data class ApplyAppVolume(val volume: Float) : SettingsRuntimeEffect
    data class SetAudioExclusiveEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetBitPerfectEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetUsbExclusiveEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetUsbClockMismatchCompatibilityEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyAudioEffects(val settings: AudioEffectSettings) : SettingsRuntimeEffect
    data class SetStatusBarLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetSystemMediaLyricsTitleEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyFloatingLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data object OpenFloatingLyricsPermissionSettings : SettingsRuntimeEffect
    data class UpdateFloatingLyricsTextSize(val textSizeSp: Int) : SettingsRuntimeEffect
    data class UpdateFloatingLyricsWidth(val widthPercent: Int) : SettingsRuntimeEffect
    data class UpdateFloatingLyricsBackgroundOpacity(val opacityPercent: Int) : SettingsRuntimeEffect
    data class UpdateFloatingLyricsTransparentBackground(val enabled: Boolean) : SettingsRuntimeEffect
    data class UpdateFloatingLyricsTextColor(val colorArgb: Int) : SettingsRuntimeEffect
    data object ShowFloatingLyrics : SettingsRuntimeEffect
    data object UnlockFloatingLyrics : SettingsRuntimeEffect
    data object ResetFloatingLyricsLayout : SettingsRuntimeEffect
    data class SetPlaybackRestoreEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetOnlineLyricsEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetLyricsOffsetMs(val offsetMs: Long) : SettingsRuntimeEffect
}
