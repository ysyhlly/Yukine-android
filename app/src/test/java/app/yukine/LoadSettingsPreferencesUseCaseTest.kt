package app.yukine

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

        val result = LoadSettingsPreferencesUseCase(operations).execute()

        assertEquals(EchoTheme.MODE_SYSTEM, result.themeMode)
        assertEquals(EchoTheme.ACCENT_BLUE, result.accentMode)
        assertEquals(AppLanguage.MODE_SYSTEM, result.languageMode)
        assertEquals(1.25f, result.playbackSpeed)
        assertEquals(0.8f, result.appVolume)
        assertEquals(StreamingQualityPreference.defaultValue(), result.streamingAudioQuality)
        assertFalse(result.concurrentPlaybackEnabled)
        assertEquals(
            listOf("theme", "accent", "language", "speed", "volume", "quality", "concurrent"),
            operations.events
        )
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
    }
}
