package app.yukine

import app.yukine.playback.AudioEffectSettings

internal class SettingsActionController(
    private val settingsViewModel: SettingsViewModel,
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun applyStreamingGatewayEndpoint(endpoint: String)

        fun applyPlaybackActionResult(result: PlaybackActionResultUi?)

        fun reloadCurrentLyrics()
    }

    fun applyThemeMode(nextMode: String) {
        settingsViewModel.applyThemeMode(nextMode)
    }

    fun applyAccentMode(nextAccent: String) {
        settingsViewModel.applyAccentMode(nextAccent)
    }

    fun applyLanguageMode(nextLanguageMode: String) {
        settingsViewModel.applyLanguageMode(nextLanguageMode)
    }

    fun applyStreamingGatewayEndpoint(endpoint: String) {
        listener.applyStreamingGatewayEndpoint(endpoint)
    }

    fun applyPlaybackSpeed(speed: Float) {
        settingsViewModel.applyPlaybackSpeed(speed)
    }

    fun applyAppVolume(volume: Float) {
        settingsViewModel.applyAppVolume(volume)
    }

    fun applyStreamingAudioQuality(quality: String) {
        settingsViewModel.applyStreamingAudioQuality(quality)
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        settingsViewModel.setConcurrentPlaybackEnabled(enabled)
    }

    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        settingsViewModel.applyAudioEffectSettings(settings)
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        settingsViewModel.setStatusBarLyricsEnabled(enabled)
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        settingsViewModel.setFloatingLyricsEnabled(enabled)
    }

    fun openFloatingLyricsPermission() {
        settingsViewModel.openFloatingLyricsPermission()
    }

    fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        settingsViewModel.setNowPlayingGesturesEnabled(enabled)
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        settingsViewModel.setPlaybackRestoreEnabled(enabled)
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        settingsViewModel.setReplayGainEnabled(enabled)
    }

    fun startSleepTimer(minutes: Int) {
        listener.applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(minutes))
    }

    fun cancelSleepTimer() {
        listener.applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer())
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        settingsViewModel.setOnlineLyricsEnabled(enabled)
    }

    fun applyLyricsOffset(offsetMs: Long) {
        settingsViewModel.applyLyricsOffset(offsetMs)
    }

    fun reloadCurrentLyrics() {
        listener.reloadCurrentLyrics()
    }
}
