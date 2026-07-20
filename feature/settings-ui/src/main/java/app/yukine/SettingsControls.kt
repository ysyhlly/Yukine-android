package app.yukine

import app.yukine.playback.AudioEffectSettings

fun interface SettingsSelectedTabProvider {
    fun selectedTab(): String
}

interface SettingsPlaybackServiceControls {
    fun setPlaybackSpeed(speed: Float)

    fun setAppVolume(volume: Float)

    fun setAudioExclusiveEnabled(enabled: Boolean)

    fun setBitPerfectEnabled(enabled: Boolean)

    fun setUsbExclusiveEnabled(enabled: Boolean)

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

    fun updateTextSize(textSizeSp: Int): Boolean = false

    fun updateWidth(widthPercent: Int): Boolean = false

    fun updateBackgroundOpacity(opacityPercent: Int): Boolean = false

    fun updateTransparentBackground(enabled: Boolean): Boolean = false

    fun updateTextColor(colorArgb: Int): Boolean = false

    fun show(): Boolean = false

    fun unlock(): Boolean = false

    fun resetLayout(): Boolean = false
}

fun interface SettingsFloatingLyricsControlsProvider {
    fun controls(): SettingsFloatingLyricsControls?
}
