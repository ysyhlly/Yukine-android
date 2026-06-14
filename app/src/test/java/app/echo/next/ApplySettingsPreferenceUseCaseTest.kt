package app.echo.next

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplySettingsPreferenceUseCaseTest {
    @Test
    fun savesEverySupportedPreference() {
        val operations = FakeSettingsPreferenceOperations()
        val useCase = ApplySettingsPreferenceUseCase(operations)

        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.ThemeMode, "dark"))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.AccentMode, "blue"))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.LanguageMode, "zh"))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.PlaybackSpeed, 1.25f))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.AppVolume, 0.7f))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.StreamingAudioQuality, "lossless"))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.OnlineLyricsEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.ConcurrentPlaybackEnabled, false))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.LyricsOffsetMs, -200L))

        assertEquals(
            listOf(
                "theme:dark",
                "accent:blue",
                "language:zh",
                "speed:1.25",
                "volume:0.7",
                "quality:lossless",
                "onlineLyrics:true",
                "concurrent:false",
                "lyricsOffset:-200"
            ),
            operations.events
        )
    }

    private class FakeSettingsPreferenceOperations : SettingsPreferenceOperations {
        val events = mutableListOf<String>()

        override fun saveThemeMode(mode: String) {
            events.add("theme:$mode")
        }

        override fun saveAccentMode(accent: String) {
            events.add("accent:$accent")
        }

        override fun saveLanguageMode(languageMode: String) {
            events.add("language:$languageMode")
        }

        override fun savePlaybackSpeed(speed: Float) {
            events.add("speed:$speed")
        }

        override fun saveAppVolume(volume: Float) {
            events.add("volume:$volume")
        }

        override fun saveStreamingAudioQuality(quality: String) {
            events.add("quality:$quality")
        }

        override fun saveOnlineLyricsEnabled(enabled: Boolean) {
            events.add("onlineLyrics:$enabled")
        }

        override fun saveConcurrentPlaybackEnabled(enabled: Boolean) {
            events.add("concurrent:$enabled")
        }

        override fun saveLyricsOffsetMs(offsetMs: Long) {
            events.add("lyricsOffset:$offsetMs")
        }
    }
}
