package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.ui.EchoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoadSettingsPreferencesUseCaseTest {
    @Test
    fun loadsAndNormalizesSettingsPreferences() {
        val operations = FakeSettingsPreferenceLoadOperations()
        operations.themeMode = "bad-theme"
        operations.accentMode = "bad-accent"
        operations.languageMode = "bad-language"
        operations.playbackSpeed = 1.25f
        operations.appVolume = 0.8f
        operations.streamingAudioQuality = "bad-quality"
        operations.concurrentPlaybackEnabled = false
        operations.statusBarLyricsEnabled = false
        operations.systemMediaLyricsTitleEnabled = true
        operations.floatingLyricsEnabled = true
        operations.nowPlayingGesturesEnabled = false
        operations.playbackRestoreEnabled = true
        operations.replayGainEnabled = false
        operations.debugPromptsEnabled = true
        operations.shareStyle = TrackShareStyle.PLATFORM_CARD
        operations.pageBackgrounds = PageBackgrounds(sharedUri = "content://all")

        val result = LoadSettingsPreferencesUseCase(operations).execute()

        assertEquals(EchoTheme.MODE_SYSTEM, result.themeMode)
        assertEquals(EchoTheme.ACCENT_BLUE, result.accentMode)
        assertEquals(AppLanguage.MODE_SYSTEM, result.languageMode)
        assertEquals(1.25f, result.playbackSpeed)
        assertEquals(0.8f, result.appVolume)
        assertEquals(StreamingQualityPreference.defaultValue(), result.streamingAudioQuality)
        assertFalse(result.concurrentPlaybackEnabled)
        assertFalse(result.audioEffectSettings.enabled)
        assertFalse(result.statusBarLyricsEnabled)
        assertEquals(true, result.systemMediaLyricsTitleEnabled)
        assertEquals(true, result.floatingLyricsEnabled)
        assertFalse(result.nowPlayingGesturesEnabled)
        assertEquals(true, result.playbackRestoreEnabled)
        assertFalse(result.replayGainEnabled)
        assertEquals(true, result.debugPromptsEnabled)
        assertEquals(TrackShareStyle.PLATFORM_CARD, result.shareStyle)
        assertEquals("content://all", result.pageBackgrounds.sharedUri)
        assertEquals(
            listOf("theme", "accent", "language", "speed", "volume", "quality", "concurrent", "effects", "statusLyrics", "floatingLyrics", "systemMediaTitle", "gestures", "restore", "replayGain", "debugPrompts", "shareStyle", "backgrounds"),
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
        var concurrentPlaybackEnabled = true
        var audioEffectSettings = AudioEffectSettings.DEFAULT
        var statusBarLyricsEnabled = true
        var systemMediaLyricsTitleEnabled = false
        var floatingLyricsEnabled = false
        var nowPlayingGesturesEnabled = true
        var playbackRestoreEnabled = true
        var replayGainEnabled = true
        var debugPromptsEnabled = false
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

        override fun loadConcurrentPlaybackEnabled(): Boolean {
            events.add("concurrent")
            return concurrentPlaybackEnabled
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

        override fun loadDebugPromptsEnabled(): Boolean {
            events.add("debugPrompts")
            return debugPromptsEnabled
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
