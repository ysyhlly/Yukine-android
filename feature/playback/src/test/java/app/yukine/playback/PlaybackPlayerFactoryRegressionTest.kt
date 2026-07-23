package app.yukine.playback

import androidx.media3.common.util.UnstableApi
import app.yukine.playback.manager.AudioOutputMode
import app.yukine.playback.manager.PlaybackPlayerFactory
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackPlayerFactoryRegressionTest {
    @Test
    fun usbExclusiveWithoutSinkCreatesDirectPcmPlayer() {
        val player = PlaybackPlayerFactory(
            RuntimeEnvironment.getApplication(),
            YukineRealtimeBassAudioProcessor(RealtimeBassDetector()),
            AudioOutputMode.USB_EXCLUSIVE,
            null
        ).createPlayer()

        try {
            assertNotNull(player)
        } finally {
            player.release()
        }
    }
}
