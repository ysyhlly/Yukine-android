package app.yukine

import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsStatusSink {
    fun set(message: String)
}

internal fun interface SettingsSelectedTabProvider {
    fun selectedTab(): String
}

internal interface SettingsPlaybackServiceControls {
    fun setPlaybackSpeed(speed: Float)

    fun setAppVolume(volume: Float)

    fun setConcurrentPlaybackEnabled(enabled: Boolean)

    fun applyAudioEffectSettings(settings: AudioEffectSettings)

    fun setStatusBarLyricsEnabled(enabled: Boolean)

    fun setPlaybackRestoreEnabled(enabled: Boolean)

    fun setReplayGainEnabled(enabled: Boolean)
}

internal fun interface SettingsPlaybackServiceControlsProvider {
    fun controls(): SettingsPlaybackServiceControls?
}

internal interface SettingsLyricsControls {
    fun setOnlineEnabled(enabled: Boolean)

    fun setOffsetMs(offsetMs: Long)
}

internal fun interface SettingsLyricsControlsProvider {
    fun controls(): SettingsLyricsControls?
}

internal interface SettingsFloatingLyricsControls {
    fun apply(enabled: Boolean): Boolean

    fun openPermissionSettings()
}

internal fun interface SettingsFloatingLyricsControlsProvider {
    fun controls(): SettingsFloatingLyricsControls?
}
