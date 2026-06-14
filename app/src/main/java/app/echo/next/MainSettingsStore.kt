package app.echo.next

import app.echo.next.ui.EchoTheme

internal class MainSettingsStore {
    private var themeMode: String = EchoTheme.MODE_SYSTEM
    private var accentMode: String = EchoTheme.ACCENT_BLUE
    private var languageMode: String = AppLanguage.MODE_SYSTEM
    private var playbackSpeed: Float = 1.0f
    private var appVolume: Float = 1.0f
    private var streamingAudioQuality: String = StreamingQualityPreference.defaultValue()
    private var concurrentPlaybackEnabled: Boolean = false

    fun load(preferences: LoadedSettingsPreferences) {
        themeMode = preferences.themeMode
        accentMode = preferences.accentMode
        languageMode = preferences.languageMode
        playbackSpeed = preferences.playbackSpeed
        appVolume = preferences.appVolume
        streamingAudioQuality = preferences.streamingAudioQuality
        concurrentPlaybackEnabled = preferences.concurrentPlaybackEnabled
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
}
