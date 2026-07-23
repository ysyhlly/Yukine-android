package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.HomeDashboardLayout
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
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.LyricsOffsetMs, -200L))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.AudioEffectSettings, AudioEffectSettings.DEFAULT.withEnabled(true)))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.StatusBarLyricsEnabled, false))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.SystemMediaLyricsTitleEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.FloatingLyricsEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.NowPlayingGesturesEnabled, false))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.PlaybackRestoreEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.ReplayGainEnabled, false))
        useCase.execute(
            SettingsPreferenceUpdate(
                SettingsPreferenceKey.UsbClockMismatchCompatibilityEnabled,
                true
            )
        )
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.DebugPromptsEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.CustomBackgroundBlurEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.CustomBackgroundBlurRadiusDp, 32f))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.GlassBlurEnabled, true))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.GlassBlurRadiusDp, 24f))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.GlassSurfaceOpacity, 1f))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.CompactSettingsCards, true))
        useCase.execute(
            SettingsPreferenceUpdate(
                SettingsPreferenceKey.HomeDashboardLayout,
                HomeDashboardLayout.Content.storageValue
            )
        )
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.ShareStyle, TrackShareStyle.PLATFORM_CARD))
        useCase.execute(SettingsPreferenceUpdate(SettingsPreferenceKey.PageBackgrounds, PageBackgrounds(sharedUri = "content://bg")))

        assertEquals(
            listOf(
                "theme:dark",
                "accent:blue",
                "language:zh",
                "speed:1.25",
                "volume:0.7",
                "quality:lossless",
                "onlineLyrics:true",
                "lyricsOffset:-200",
                "effects:true",
                "statusLyrics:false",
                "systemMediaTitle:true",
                "floatingLyrics:true",
                "gestures:false",
                "restore:true",
                "replayGain:false",
                "usbClockMismatchCompatibility:true",
                "debugPrompts:true",
                "customBackgroundBlurEnabled:true",
                "customBackgroundBlurRadius:32.0",
                "glassBlurEnabled:true",
                "glassBlurRadius:24.0",
                "glassSurfaceOpacity:1.0",
                "compactSettingsCards:true",
                "homeDashboardLayout:content",
                "shareStyle:${TrackShareStyle.PLATFORM_CARD}",
                "background:content://bg"
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

        override fun saveRefuseAutomaticQualityDowngrade(refuse: Boolean) {
            events.add("refuseQualityDowngrade:$refuse")
        }

        override fun saveOnlineLyricsEnabled(enabled: Boolean) {
            events.add("onlineLyrics:$enabled")
        }

        override fun saveLyricsOffsetMs(offsetMs: Long) {
            events.add("lyricsOffset:$offsetMs")
        }

        override fun saveAudioEffectSettings(settings: AudioEffectSettings) {
            events.add("effects:${settings.enabled}")
        }

        override fun saveStatusBarLyricsEnabled(enabled: Boolean) {
            events.add("statusLyrics:$enabled")
        }

        override fun saveSystemMediaLyricsTitleEnabled(enabled: Boolean) {
            events.add("systemMediaTitle:$enabled")
        }

        override fun saveFloatingLyricsEnabled(enabled: Boolean) {
            events.add("floatingLyrics:$enabled")
        }

        override fun saveNowPlayingGesturesEnabled(enabled: Boolean) {
            events.add("gestures:$enabled")
        }

        override fun savePlaybackRestoreEnabled(enabled: Boolean) {
            events.add("restore:$enabled")
        }

        override fun saveReplayGainEnabled(enabled: Boolean) {
            events.add("replayGain:$enabled")
        }

        override fun saveAudioExclusiveEnabled(enabled: Boolean) {
            events.add("audioExclusive:$enabled")
        }

        override fun saveBitPerfectEnabled(enabled: Boolean) {
            events.add("bitPerfect:$enabled")
        }

        override fun saveUsbExclusiveEnabled(enabled: Boolean) {
            events.add("usbExclusive:$enabled")
        }

        override fun saveUsbClockMismatchCompatibilityEnabled(enabled: Boolean) {
            events.add("usbClockMismatchCompatibility:$enabled")
        }

        override fun saveDebugPromptsEnabled(enabled: Boolean) {
            events.add("debugPrompts:$enabled")
        }

        override fun saveCheckUpdateEnabled(enabled: Boolean) {
            events.add("checkUpdateEnabled:$enabled")
        }

        override fun saveCustomBackgroundBlurEnabled(enabled: Boolean) {
            events.add("customBackgroundBlurEnabled:$enabled")
        }

        override fun saveCustomBackgroundBlurRadiusDp(radiusDp: Float) {
            events.add("customBackgroundBlurRadius:$radiusDp")
        }

        override fun saveGlassBlurEnabled(enabled: Boolean) {
            events.add("glassBlurEnabled:$enabled")
        }

        override fun saveGlassBlurRadiusDp(radiusDp: Float) {
            events.add("glassBlurRadius:$radiusDp")
        }

        override fun saveGlassSurfaceOpacity(opacity: Float) {
            events.add("glassSurfaceOpacity:$opacity")
        }

        override fun saveCompactSettingsCards(enabled: Boolean) {
            events.add("compactSettingsCards:$enabled")
        }

        override fun saveHomeDashboardLayout(layout: String) {
            events.add("homeDashboardLayout:$layout")
        }

        override fun saveShareStyle(style: String) {
            events.add("shareStyle:$style")
        }

        override fun savePageBackgrounds(backgrounds: PageBackgrounds) {
            events.add("background:${backgrounds.sharedUri}")
        }
    }
}
