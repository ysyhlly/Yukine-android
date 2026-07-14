package app.yukine

import app.yukine.playback.AudioEffectSettings

sealed interface SettingsRuntimeEffect {
    data object ApplyThemeSurface : SettingsRuntimeEffect
    data class RefreshCustomBackgroundAccent(val backgrounds: PageBackgrounds) : SettingsRuntimeEffect
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsRuntimeEffect
    data class ApplyAppVolume(val volume: Float) : SettingsRuntimeEffect
    data class SetConcurrentPlaybackEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyAudioEffects(val settings: AudioEffectSettings) : SettingsRuntimeEffect
    data class SetStatusBarLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetSystemMediaLyricsTitleEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyFloatingLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data object OpenFloatingLyricsPermissionSettings : SettingsRuntimeEffect
    data class SetPlaybackRestoreEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetOnlineLyricsEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetLyricsOffsetMs(val offsetMs: Long) : SettingsRuntimeEffect
}
