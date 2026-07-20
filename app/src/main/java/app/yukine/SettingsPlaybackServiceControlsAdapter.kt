package app.yukine

import app.yukine.playback.AudioEffectSettings

interface SettingsPlaybackServicePort {
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

internal class MainSettingsPlaybackServiceControls(
    private val service: SettingsPlaybackServicePort
) : SettingsPlaybackServiceControls {
    override fun setPlaybackSpeed(speed: Float) {
        service.setPlaybackSpeed(speed)
    }

    override fun setAppVolume(volume: Float) {
        service.setAppVolume(volume)
    }

    override fun setAudioExclusiveEnabled(enabled: Boolean) {
        service.setAudioExclusiveEnabled(enabled)
    }

    override fun setBitPerfectEnabled(enabled: Boolean) {
        service.setBitPerfectEnabled(enabled)
    }

    override fun setUsbExclusiveEnabled(enabled: Boolean) {
        service.setUsbExclusiveEnabled(enabled)
    }

    override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        service.applyAudioEffectSettings(settings)
    }

    override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        service.setStatusBarLyricsEnabled(enabled)
    }

    override fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        service.setSystemMediaLyricsTitleEnabled(enabled)
    }

    override fun setPlaybackRestoreEnabled(enabled: Boolean) {
        service.setPlaybackRestoreEnabled(enabled)
    }

    override fun setReplayGainEnabled(enabled: Boolean) {
        service.setReplayGainEnabled(enabled)
    }
}
