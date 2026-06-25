package app.yukine

import app.yukine.playback.AudioEffectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRuntimeApplierTest {
    @Test
    fun appliesRuntimeSettingsThroughDedicatedControls() {
        val calls = mutableListOf<String>()
        var floatingAllowed = true
        val playbackControls = object : SettingsPlaybackServiceControls {
            override fun setPlaybackSpeed(speed: Float) {
                calls += "speed:$speed"
            }

            override fun setAppVolume(volume: Float) {
                calls += "volume:$volume"
            }

            override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
                calls += "concurrent:$enabled"
            }

            override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
                calls += "effects:${settings.enabled}"
            }

            override fun setStatusBarLyricsEnabled(enabled: Boolean) {
                calls += "statusLyrics:$enabled"
            }

            override fun setPlaybackRestoreEnabled(enabled: Boolean) {
                calls += "restore:$enabled"
            }

            override fun setReplayGainEnabled(enabled: Boolean) {
                calls += "replayGain:$enabled"
            }
        }
        val lyricsControls = object : SettingsLyricsControls {
            override fun setOnlineEnabled(enabled: Boolean) {
                calls += "onlineLyrics:$enabled"
            }

            override fun setOffsetMs(offsetMs: Long) {
                calls += "lyricsOffset:$offsetMs"
            }
        }
        val floatingControls = object : SettingsFloatingLyricsControls {
            override fun apply(enabled: Boolean): Boolean {
                calls += "floating:$enabled"
                return floatingAllowed
            }

            override fun openPermissionSettings() {
                calls += "floatingPermission"
            }
        }
        val applier = SettingsRuntimeApplier(
            applyThemeSurfaceAction = SettingsThemeSurfaceApplier { calls += "theme" },
            updateLanguageAction = SettingsRuntimeLanguageUpdater { language -> calls += "language:$language" },
            playbackServiceControlsProvider = SettingsPlaybackServiceControlsProvider { playbackControls },
            lyricsControlsProvider = SettingsLyricsControlsProvider { lyricsControls },
            floatingLyricsControlsProvider = SettingsFloatingLyricsControlsProvider { floatingControls }
        )

        assertTrue(applier.apply(SettingsRuntimeEffect.ApplyThemeSurface))
        assertTrue(applier.apply(SettingsRuntimeEffect.UpdateLanguage(AppLanguage.MODE_ENGLISH)))
        assertTrue(applier.apply(SettingsRuntimeEffect.ApplyPlaybackSpeed(1.25f)))
        assertTrue(applier.apply(SettingsRuntimeEffect.ApplyAppVolume(0.75f)))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(true)))
        assertTrue(applier.apply(SettingsRuntimeEffect.ApplyAudioEffects(AudioEffectSettings.DEFAULT.withEnabled(true))))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetStatusBarLyrics(false)))
        assertTrue(applier.apply(SettingsRuntimeEffect.ApplyFloatingLyrics(true)))
        floatingAllowed = false
        assertFalse(applier.apply(SettingsRuntimeEffect.ApplyFloatingLyrics(true)))
        assertTrue(applier.apply(SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(true)))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetReplayGainEnabled(false)))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetOnlineLyricsEnabled(true)))
        assertTrue(applier.apply(SettingsRuntimeEffect.SetLyricsOffsetMs(320L)))

        assertEquals(
            listOf(
                "theme",
                "language:en",
                "speed:1.25",
                "volume:0.75",
                "concurrent:true",
                "effects:true",
                "statusLyrics:false",
                "floating:true",
                "floating:true",
                "floatingPermission",
                "restore:true",
                "replayGain:false",
                "onlineLyrics:true",
                "lyricsOffset:320"
            ),
            calls
        )
    }
}
