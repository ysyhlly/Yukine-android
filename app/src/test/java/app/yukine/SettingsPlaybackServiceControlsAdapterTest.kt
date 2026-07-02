package app.yukine

import app.yukine.playback.AudioEffectSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPlaybackServiceControlsAdapterTest {
    @Test
    fun forwardsRuntimeSettingsThroughServicePort() {
        val service = FakeSettingsPlaybackServicePort()
        val controls = MainSettingsPlaybackServiceControls(service)

        controls.setPlaybackSpeed(1.25f)
        controls.setAppVolume(0.75f)
        controls.setConcurrentPlaybackEnabled(true)
        controls.applyAudioEffectSettings(AudioEffectSettings.DEFAULT.withEnabled(true))
        controls.setStatusBarLyricsEnabled(false)
        controls.setPlaybackRestoreEnabled(true)
        controls.setReplayGainEnabled(false)

        assertEquals(
            listOf(
                "speed:1.25",
                "volume:0.75",
                "concurrent:true",
                "effects:true",
                "statusLyrics:false",
                "restore:true",
                "replayGain:false"
            ),
            service.calls
        )
    }

    private class FakeSettingsPlaybackServicePort : SettingsPlaybackServicePort {
        val calls = mutableListOf<String>()

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
}
