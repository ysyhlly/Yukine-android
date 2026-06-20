package app.yukine

import app.yukine.playback.AudioEffectSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsControlsBindingsTest {
    @Test
    fun playbackControlsDelegateToSetters() {
        val calls = mutableListOf<String>()
        val controls = SettingsPlaybackServiceControlsBindings(
            SettingsPlaybackSpeedSetter { calls += "speed:$it" },
            SettingsAppVolumeSetter { calls += "volume:$it" },
            SettingsConcurrentPlaybackSetter { calls += "concurrent:$it" },
            SettingsAudioEffectsSetter { calls += "effects:${it.enabled}" },
            SettingsStatusBarLyricsSetter { calls += "statusLyrics:$it" },
            SettingsPlaybackRestoreSetter { calls += "restore:$it" },
            SettingsReplayGainSetter { calls += "replayGain:$it" }
        )

        controls.setPlaybackSpeed(1.25f)
        controls.setAppVolume(0.8f)
        controls.setConcurrentPlaybackEnabled(true)
        controls.applyAudioEffectSettings(AudioEffectSettings.DEFAULT.withEnabled(true))
        controls.setStatusBarLyricsEnabled(false)
        controls.setPlaybackRestoreEnabled(true)
        controls.setReplayGainEnabled(false)

        assertEquals(
            listOf("speed:1.25", "volume:0.8", "concurrent:true", "effects:true", "statusLyrics:false", "restore:true", "replayGain:false"),
            calls
        )
    }

    @Test
    fun lyricsControlsDelegateToSetters() {
        val calls = mutableListOf<String>()
        val controls = SettingsLyricsControlsBindings(
            SettingsOnlineLyricsSetter { calls += "online:$it" },
            SettingsLyricsOffsetSetter { calls += "offset:$it" }
        )

        controls.setOnlineEnabled(false)
        controls.setOffsetMs(350L)

        assertEquals(listOf("online:false", "offset:350"), calls)
    }
}
