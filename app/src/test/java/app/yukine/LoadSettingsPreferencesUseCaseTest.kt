package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoadSettingsPreferencesUseCaseTest {
    @Test
    fun migratesLegacyDynamicThemeToSystemThemeAndDynamicAccent() {
        val operations = FakeSettingsPreferenceLoadOperations().apply {
            themeMode = EchoTheme.MODE_DYNAMIC
            accentMode = EchoTheme.ACCENT_ROSE
        }

        val result = LoadSettingsPreferencesUseCase(operations).execute()

        assertEquals(EchoTheme.MODE_SYSTEM, result.themeMode)
        assertEquals(EchoTheme.ACCENT_DYNAMIC_SYSTEM, result.accentMode)
    }

    @Test
    fun loadsAndNormalizesSettingsPreferences() {
        val operations = FakeSettingsPreferenceLoadOperations()
        operations.themeMode = "bad-theme"
        operations.accentMode = "bad-accent"
        operations.languageMode = "bad-language"
        operations.playbackSpeed = 1.25f
        operations.appVolume = 0.8f
        operations.streamingAudioQuality = "bad-quality"
        operations.refuseAutomaticQualityDowngrade = true
        operations.statusBarLyricsEnabled = false
        operations.systemMediaLyricsTitleEnabled = true
        operations.floatingLyricsEnabled = true
        operations.nowPlayingGesturesEnabled = false
        operations.playbackRestoreEnabled = true
        operations.replayGainEnabled = false
        operations.usbClockMismatchCompatibilityEnabled = true
        operations.debugPromptsEnabled = true
        operations.customBackgroundBlurEnabled = true
        operations.customBackgroundBlurRadiusDp = 32f
        operations.glassBlurEnabled = true
        operations.glassBlurRadiusDp = 24f
        operations.glassSurfaceOpacity = 1f
        operations.compactSettingsCards = true
        operations.homeDashboardLayout = HomeDashboardLayout.Content.storageValue
        operations.shareStyle = TrackShareStyle.PLATFORM_CARD
        operations.pageBackgrounds = PageBackgrounds(sharedUri = "content://all")

        val result = LoadSettingsPreferencesUseCase(operations).execute()

        assertEquals(EchoTheme.MODE_SYSTEM, result.themeMode)
        assertEquals(EchoTheme.ACCENT_BLUE, result.accentMode)
        assertEquals(AppLanguage.MODE_SYSTEM, result.languageMode)
        assertEquals(1.25f, result.playbackSpeed)
        assertEquals(0.8f, result.appVolume)
        assertEquals(StreamingQualityPreference.defaultValue(), result.streamingAudioQuality)
        assertEquals(true, result.refuseAutomaticQualityDowngrade)
        assertFalse(result.audioEffectSettings.enabled)
        assertFalse(result.statusBarLyricsEnabled)
        assertEquals(true, result.systemMediaLyricsTitleEnabled)
        assertEquals(true, result.floatingLyricsEnabled)
        assertFalse(result.nowPlayingGesturesEnabled)
        assertEquals(true, result.playbackRestoreEnabled)
        assertFalse(result.replayGainEnabled)
        assertEquals(true, result.usbClockMismatchCompatibilityEnabled)
        assertEquals(true, result.debugPromptsEnabled)
        assertEquals(true, result.customBackgroundBlurEnabled)
        assertEquals(32f, result.customBackgroundBlurRadiusDp)
        assertEquals(true, result.glassBlurEnabled)
        assertEquals(24f, result.glassBlurRadiusDp)
        assertEquals(1f, result.glassSurfaceOpacity)
        assertEquals(true, result.compactSettingsCards)
        assertEquals(HomeDashboardLayout.Content, result.homeDashboardLayout)
        assertEquals(TrackShareStyle.PLATFORM_CARD, result.shareStyle)
        assertEquals("content://all", result.pageBackgrounds.sharedUri)
        assertEquals(
            listOf("theme", "accent", "language", "speed", "volume", "quality", "refuseQualityDowngrade", "effects", "statusLyrics", "floatingLyrics", "systemMediaTitle", "gestures", "restore", "replayGain", "audioExclusive", "bitPerfect", "usbExclusive", "usbClockMismatchCompatibility", "debugPrompts", "checkUpdateEnabled", "customBackgroundBlurEnabled", "customBackgroundBlurRadius", "glassBlurEnabled", "glassBlurRadius", "glassSurfaceOpacity", "compactSettingsCards", "homeDashboardLayout", "shareStyle", "backgrounds"),
            operations.events
        )
    }

    @Test
    fun floatingLyricsWinsWhenStoredStatusBarLyricsIsAlsoEnabled() {
        val operations = FakeSettingsPreferenceLoadOperations()
        operations.statusBarLyricsEnabled = true
        operations.floatingLyricsEnabled = true

        val result = LoadSettingsPreferencesUseCase(operations).execute()

        assertFalse(result.statusBarLyricsEnabled)
        assertEquals(true, result.floatingLyricsEnabled)
    }

    private class FakeSettingsPreferenceLoadOperations : SettingsPreferenceLoadOperations {
        val events = mutableListOf<String>()
        var themeMode = EchoTheme.MODE_SYSTEM
        var accentMode = EchoTheme.ACCENT_BLUE
        var languageMode = AppLanguage.MODE_SYSTEM
        var playbackSpeed = 1.0f
        var appVolume = 1.0f
        var streamingAudioQuality = StreamingQualityPreference.defaultValue()
        var refuseAutomaticQualityDowngrade = false
        var audioEffectSettings = AudioEffectSettings.DEFAULT
        var statusBarLyricsEnabled = true
        var systemMediaLyricsTitleEnabled = false
        var floatingLyricsEnabled = false
        var nowPlayingGesturesEnabled = true
        var playbackRestoreEnabled = true
        var replayGainEnabled = true
        var usbClockMismatchCompatibilityEnabled = false
        var debugPromptsEnabled = false
        var customBackgroundBlurEnabled = false
        var customBackgroundBlurRadiusDp = 24f
        var glassBlurEnabled = false
        var glassBlurRadiusDp = 18f
        var glassSurfaceOpacity = 0.62f
        var compactSettingsCards = false
        var homeDashboardLayout = HomeDashboardLayout.Classic.storageValue
        var shareStyle = TrackShareStyle.TEXT
        var pageBackgrounds = PageBackgrounds.empty()

        override fun loadThemeMode(): String {
            events.add("theme")
            return themeMode
        }

        override fun loadAccentMode(): String {
            events.add("accent")
            return accentMode
        }

        override fun loadLanguageMode(): String {
            events.add("language")
            return languageMode
        }

        override fun loadPlaybackSpeed(): Float {
            events.add("speed")
            return playbackSpeed
        }

        override fun loadAppVolume(): Float {
            events.add("volume")
            return appVolume
        }

        override fun loadStreamingAudioQuality(): String {
            events.add("quality")
            return streamingAudioQuality
        }

        override fun loadRefuseAutomaticQualityDowngrade(): Boolean {
            events.add("refuseQualityDowngrade")
            return refuseAutomaticQualityDowngrade
        }

        override fun loadAudioEffectSettings(): AudioEffectSettings {
            events.add("effects")
            return audioEffectSettings
        }

        override fun loadStatusBarLyricsEnabled(): Boolean {
            events.add("statusLyrics")
            return statusBarLyricsEnabled
        }

        override fun loadSystemMediaLyricsTitleEnabled(): Boolean {
            events.add("systemMediaTitle")
            return systemMediaLyricsTitleEnabled
        }

        override fun loadFloatingLyricsEnabled(): Boolean {
            events.add("floatingLyrics")
            return floatingLyricsEnabled
        }

        override fun loadNowPlayingGesturesEnabled(): Boolean {
            events.add("gestures")
            return nowPlayingGesturesEnabled
        }

        override fun loadPlaybackRestoreEnabled(): Boolean {
            events.add("restore")
            return playbackRestoreEnabled
        }

        override fun loadReplayGainEnabled(): Boolean {
            events.add("replayGain")
            return replayGainEnabled
        }

        override fun loadAudioExclusiveEnabled(): Boolean {
            events.add("audioExclusive")
            return true
        }

        override fun loadBitPerfectEnabled(): Boolean {
            events.add("bitPerfect")
            return false
        }

        override fun loadUsbExclusiveEnabled(): Boolean {
            events.add("usbExclusive")
            return false
        }

        override fun loadUsbClockMismatchCompatibilityEnabled(): Boolean {
            events.add("usbClockMismatchCompatibility")
            return usbClockMismatchCompatibilityEnabled
        }

        override fun loadDebugPromptsEnabled(): Boolean {
            events.add("debugPrompts")
            return debugPromptsEnabled
        }

        override fun loadCheckUpdateEnabled(): Boolean {
            events.add("checkUpdateEnabled")
            return true
        }

        override fun loadCustomBackgroundBlurEnabled(): Boolean {
            events.add("customBackgroundBlurEnabled")
            return customBackgroundBlurEnabled
        }

        override fun loadCustomBackgroundBlurRadiusDp(): Float {
            events.add("customBackgroundBlurRadius")
            return customBackgroundBlurRadiusDp
        }

        override fun loadGlassBlurEnabled(): Boolean {
            events.add("glassBlurEnabled")
            return glassBlurEnabled
        }

        override fun loadGlassBlurRadiusDp(): Float {
            events.add("glassBlurRadius")
            return glassBlurRadiusDp
        }

        override fun loadGlassSurfaceOpacity(): Float {
            events.add("glassSurfaceOpacity")
            return glassSurfaceOpacity
        }

        override fun loadCompactSettingsCards(): Boolean {
            events.add("compactSettingsCards")
            return compactSettingsCards
        }

        override fun loadHomeDashboardLayout(): String {
            events.add("homeDashboardLayout")
            return homeDashboardLayout
        }

        override fun loadShareStyle(): String {
            events.add("shareStyle")
            return shareStyle
        }

        override fun loadPageBackgrounds(): PageBackgrounds {
            events.add("backgrounds")
            return pageBackgrounds
        }
    }
}
