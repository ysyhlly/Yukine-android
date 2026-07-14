package app.yukine

import app.yukine.playback.AudioEffectSettings

fun interface SettingsSelectedTabProvider {
    fun selectedTab(): String
}

interface SettingsPlaybackServiceControls {
    fun setPlaybackSpeed(speed: Float)

    fun setAppVolume(volume: Float)

    fun setConcurrentPlaybackEnabled(enabled: Boolean)

    fun applyAudioEffectSettings(settings: AudioEffectSettings)

    fun setStatusBarLyricsEnabled(enabled: Boolean)

    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean)

    fun setPlaybackRestoreEnabled(enabled: Boolean)

    fun setReplayGainEnabled(enabled: Boolean)
}

fun interface SettingsPlaybackServiceControlsProvider {
    fun controls(): SettingsPlaybackServiceControls?
}

interface SettingsLyricsControls {
    fun setOnlineEnabled(enabled: Boolean)

    fun setOffsetMs(offsetMs: Long)
}

fun interface SettingsLyricsControlsProvider {
    fun controls(): SettingsLyricsControls?
}

interface SettingsFloatingLyricsControls {
    fun apply(enabled: Boolean): Boolean

    fun openPermissionSettings(): Boolean
}

fun interface SettingsFloatingLyricsControlsProvider {
    fun controls(): SettingsFloatingLyricsControls?
}
