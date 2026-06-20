package app.yukine

import app.yukine.ui.EchoTheme
import app.yukine.playback.AudioEffectSettings

internal class MainSettingsStore {
    private var themeMode: String = EchoTheme.MODE_SYSTEM
    private var accentMode: String = EchoTheme.ACCENT_BLUE
    private var languageMode: String = AppLanguage.MODE_SYSTEM
    private var playbackSpeed: Float = 1.0f
    private var appVolume: Float = 1.0f
    private var streamingAudioQuality: String = StreamingQualityPreference.defaultValue()
    private var concurrentPlaybackEnabled: Boolean = false
    private var audioEffectSettings: AudioEffectSettings = AudioEffectSettings.DEFAULT
    private var statusBarLyricsEnabled: Boolean = true
    private var floatingLyricsEnabled: Boolean = false
    private var nowPlayingGesturesEnabled: Boolean = true
    private var playbackRestoreEnabled: Boolean = true
    private var replayGainEnabled: Boolean = true

    fun load(preferences: LoadedSettingsPreferences) {
        themeMode = preferences.themeMode
        accentMode = preferences.accentMode
        languageMode = preferences.languageMode
        playbackSpeed = preferences.playbackSpeed
        appVolume = preferences.appVolume
        streamingAudioQuality = preferences.streamingAudioQuality
        concurrentPlaybackEnabled = preferences.concurrentPlaybackEnabled
        audioEffectSettings = preferences.audioEffectSettings
        statusBarLyricsEnabled = preferences.statusBarLyricsEnabled
        floatingLyricsEnabled = preferences.floatingLyricsEnabled
        nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled
        playbackRestoreEnabled = preferences.playbackRestoreEnabled
        replayGainEnabled = preferences.replayGainEnabled
        EchoTheme.setMode(themeMode)
        EchoTheme.setAccent(accentMode)
    }

    fun themeMode(): String {
        return themeMode
    }

    fun accentMode(): String {
        return accentMode
    }

    fun languageMode(): String {
        return languageMode
    }

    fun playbackSpeed(): Float {
        return playbackSpeed
    }

    fun appVolume(): Float {
        return appVolume
    }

    fun streamingAudioQuality(): String {
        return streamingAudioQuality
    }

    fun concurrentPlaybackEnabled(): Boolean {
        return concurrentPlaybackEnabled
    }

    fun audioEffectSettings(): AudioEffectSettings {
        return audioEffectSettings
    }

    fun statusBarLyricsEnabled(): Boolean {
        return statusBarLyricsEnabled
    }

    fun floatingLyricsEnabled(): Boolean {
        return floatingLyricsEnabled
    }

    fun nowPlayingGesturesEnabled(): Boolean {
        return nowPlayingGesturesEnabled
    }

    fun playbackRestoreEnabled(): Boolean {
        return playbackRestoreEnabled
    }

    fun replayGainEnabled(): Boolean {
        return replayGainEnabled
    }

    fun setThemeMode(themeMode: String) {
        this.themeMode = EchoTheme.normalizeMode(themeMode)
    }

    fun setAccentMode(accentMode: String) {
        this.accentMode = EchoTheme.normalizeAccent(accentMode)
    }

    fun setLanguageMode(languageMode: String) {
        this.languageMode = AppLanguage.normalizeMode(languageMode)
    }

    fun setPlaybackSpeed(playbackSpeed: Float) {
        this.playbackSpeed = playbackSpeed
    }

    fun setAppVolume(appVolume: Float) {
        this.appVolume = appVolume
    }

    fun setStreamingAudioQuality(streamingAudioQuality: String) {
        this.streamingAudioQuality = StreamingQualityPreference.normalize(streamingAudioQuality)
    }

    fun setConcurrentPlaybackEnabled(concurrentPlaybackEnabled: Boolean) {
        this.concurrentPlaybackEnabled = concurrentPlaybackEnabled
    }

    fun setAudioEffectSettings(audioEffectSettings: AudioEffectSettings?) {
        this.audioEffectSettings = audioEffectSettings ?: AudioEffectSettings.DEFAULT
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        this.statusBarLyricsEnabled = enabled
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        this.floatingLyricsEnabled = enabled
    }

    fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        this.nowPlayingGesturesEnabled = enabled
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        this.playbackRestoreEnabled = enabled
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        this.replayGainEnabled = enabled
    }
}
