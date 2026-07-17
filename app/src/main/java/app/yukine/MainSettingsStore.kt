package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import app.yukine.ui.EchoTheme
import app.yukine.playback.AudioEffectSettings

internal class MainSettingsStore {
    private var themeMode: String = EchoTheme.MODE_SYSTEM
    private var accentMode: String = EchoTheme.ACCENT_BLUE
    private var languageMode: String = AppLanguage.MODE_SYSTEM
    private var playbackSpeed: Float = 1.0f
    private var appVolume: Float = 1.0f
    private var streamingAudioQuality: String = StreamingQualityPreference.defaultValue()
    private var refuseAutomaticQualityDowngrade: Boolean = false
    private var concurrentPlaybackEnabled: Boolean = false
    private var audioEffectSettings: AudioEffectSettings = AudioEffectSettings.DEFAULT
    private var statusBarLyricsEnabled: Boolean = true
    private var systemMediaLyricsTitleEnabled: Boolean = false
    private var floatingLyricsEnabled: Boolean = false
    private var nowPlayingGesturesEnabled: Boolean = true
    private var playbackRestoreEnabled: Boolean = true
    private var replayGainEnabled: Boolean = true
    private var debugPromptsEnabled: Boolean = false
    private var customBackgroundBlurEnabled: Boolean = false
    private var customBackgroundBlurRadiusDp: Float =
        app.yukine.ui.EchoBackgroundBlurDefaults.DEFAULT_RADIUS_DP
    private var glassBlurEnabled: Boolean = false
    private var glassBlurRadiusDp: Float = app.yukine.ui.EchoGlassDefaults.BLUR_RADIUS_DP
    private var glassSurfaceOpacity: Float = app.yukine.ui.EchoGlassDefaults.SURFACE_OPACITY
    private var compactSettingsCards: Boolean = false
    private var shareStyle: String = TrackShareStyle.defaultValue()
    private var pageBackgrounds: PageBackgrounds = PageBackgrounds.empty()

    fun load(preferences: LoadedSettingsPreferences) {
        themeMode = preferences.themeMode
        accentMode = preferences.accentMode
        languageMode = preferences.languageMode
        playbackSpeed = preferences.playbackSpeed
        appVolume = preferences.appVolume
        streamingAudioQuality = preferences.streamingAudioQuality
        refuseAutomaticQualityDowngrade = preferences.refuseAutomaticQualityDowngrade
        concurrentPlaybackEnabled = preferences.concurrentPlaybackEnabled
        audioEffectSettings = preferences.audioEffectSettings
        statusBarLyricsEnabled = preferences.statusBarLyricsEnabled && !preferences.floatingLyricsEnabled
        systemMediaLyricsTitleEnabled = preferences.systemMediaLyricsTitleEnabled
        floatingLyricsEnabled = preferences.floatingLyricsEnabled
        nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled
        playbackRestoreEnabled = preferences.playbackRestoreEnabled
        replayGainEnabled = preferences.replayGainEnabled
        debugPromptsEnabled = preferences.debugPromptsEnabled
        customBackgroundBlurEnabled = preferences.customBackgroundBlurEnabled
        customBackgroundBlurRadiusDp = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(
            preferences.customBackgroundBlurRadiusDp
        )
        glassBlurEnabled = preferences.glassBlurEnabled
        glassBlurRadiusDp = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(preferences.glassBlurRadiusDp)
        glassSurfaceOpacity = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(preferences.glassSurfaceOpacity)
        compactSettingsCards = preferences.compactSettingsCards
        shareStyle = preferences.shareStyle
        pageBackgrounds = preferences.pageBackgrounds
        EchoTheme.setMode(themeMode)
        EchoTheme.setAccent(accentMode)
    }

    fun sync(preferences: SettingsPreferencesSnapshot) {
        themeMode = EchoTheme.normalizeMode(preferences.themeMode)
        accentMode = EchoTheme.normalizeAccent(preferences.accentMode)
        languageMode = AppLanguage.normalizeMode(preferences.languageMode)
        playbackSpeed = preferences.playbackSpeed
        appVolume = preferences.appVolume
        streamingAudioQuality = StreamingQualityPreference.normalize(preferences.streamingAudioQuality)
        refuseAutomaticQualityDowngrade = preferences.refuseAutomaticQualityDowngrade
        concurrentPlaybackEnabled = preferences.concurrentPlaybackEnabled
        audioEffectSettings = preferences.audioEffectSettings
        statusBarLyricsEnabled = preferences.statusBarLyricsEnabled && !preferences.floatingLyricsEnabled
        systemMediaLyricsTitleEnabled = preferences.systemMediaLyricsTitleEnabled
        floatingLyricsEnabled = preferences.floatingLyricsEnabled
        nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled
        playbackRestoreEnabled = preferences.playbackRestoreEnabled
        replayGainEnabled = preferences.replayGainEnabled
        debugPromptsEnabled = preferences.debugPromptsEnabled
        customBackgroundBlurEnabled = preferences.customBackgroundBlurEnabled
        customBackgroundBlurRadiusDp = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(
            preferences.customBackgroundBlurRadiusDp
        )
        glassBlurEnabled = preferences.glassBlurEnabled
        glassBlurRadiusDp = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(preferences.glassBlurRadiusDp)
        glassSurfaceOpacity = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(preferences.glassSurfaceOpacity)
        compactSettingsCards = preferences.compactSettingsCards
        shareStyle = TrackShareStyle.normalize(preferences.shareStyle)
        pageBackgrounds = preferences.pageBackgrounds
    }

    fun preferencesSnapshot(): SettingsPreferencesSnapshot =
        SettingsPreferencesSnapshot(
            themeMode = themeMode,
            accentMode = accentMode,
            languageMode = languageMode,
            playbackSpeed = playbackSpeed,
            appVolume = appVolume,
            streamingAudioQuality = streamingAudioQuality,
            refuseAutomaticQualityDowngrade = refuseAutomaticQualityDowngrade,
            concurrentPlaybackEnabled = concurrentPlaybackEnabled,
            audioEffectSettings = audioEffectSettings,
            statusBarLyricsEnabled = statusBarLyricsEnabled,
            systemMediaLyricsTitleEnabled = systemMediaLyricsTitleEnabled,
            floatingLyricsEnabled = floatingLyricsEnabled,
            nowPlayingGesturesEnabled = nowPlayingGesturesEnabled,
            playbackRestoreEnabled = playbackRestoreEnabled,
            replayGainEnabled = replayGainEnabled,
            debugPromptsEnabled = debugPromptsEnabled,
            customBackgroundBlurEnabled = customBackgroundBlurEnabled,
            customBackgroundBlurRadiusDp = customBackgroundBlurRadiusDp,
            glassBlurEnabled = glassBlurEnabled,
            glassBlurRadiusDp = glassBlurRadiusDp,
            glassSurfaceOpacity = glassSurfaceOpacity,
            compactSettingsCards = compactSettingsCards,
            shareStyle = shareStyle,
            pageBackgrounds = pageBackgrounds
        )

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

    fun refuseAutomaticQualityDowngrade(): Boolean = refuseAutomaticQualityDowngrade

    fun concurrentPlaybackEnabled(): Boolean {
        return concurrentPlaybackEnabled
    }

    fun audioEffectSettings(): AudioEffectSettings {
        return audioEffectSettings
    }

    fun statusBarLyricsEnabled(): Boolean {
        return statusBarLyricsEnabled
    }

    fun systemMediaLyricsTitleEnabled(): Boolean {
        return systemMediaLyricsTitleEnabled
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

    fun debugPromptsEnabled(): Boolean {
        return debugPromptsEnabled
    }

    fun customBackgroundBlurEnabled(): Boolean = customBackgroundBlurEnabled

    fun customBackgroundBlurRadiusDp(): Float = customBackgroundBlurRadiusDp

    fun glassBlurEnabled(): Boolean = glassBlurEnabled

    fun glassBlurRadiusDp(): Float = glassBlurRadiusDp
    fun glassSurfaceOpacity(): Float = glassSurfaceOpacity
    fun compactSettingsCards(): Boolean = compactSettingsCards

    fun shareStyle(): String {
        return shareStyle
    }

    fun pageBackgrounds(): PageBackgrounds {
        return pageBackgrounds
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

    fun setRefuseAutomaticQualityDowngrade(refuse: Boolean) {
        refuseAutomaticQualityDowngrade = refuse
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

    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        this.systemMediaLyricsTitleEnabled = enabled
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

    fun setDebugPromptsEnabled(enabled: Boolean) {
        this.debugPromptsEnabled = enabled
    }

    fun setCustomBackgroundBlurEnabled(enabled: Boolean) {
        this.customBackgroundBlurEnabled = enabled
    }

    fun setCustomBackgroundBlurRadiusDp(radiusDp: Float) {
        this.customBackgroundBlurRadiusDp =
            app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(radiusDp)
    }

    fun setGlassBlurEnabled(enabled: Boolean) {
        this.glassBlurEnabled = enabled
    }

    fun setGlassBlurRadiusDp(radiusDp: Float) {
        this.glassBlurRadiusDp = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(radiusDp)
    }

    fun setGlassSurfaceOpacity(opacity: Float) {
        this.glassSurfaceOpacity = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(opacity)
    }

    fun setCompactSettingsCards(enabled: Boolean) {
        this.compactSettingsCards = enabled
    }

    fun setShareStyle(style: String) {
        this.shareStyle = TrackShareStyle.normalize(style)
    }

    fun setPageBackgrounds(backgrounds: PageBackgrounds?) {
        this.pageBackgrounds = backgrounds ?: PageBackgrounds.empty()
    }
}
