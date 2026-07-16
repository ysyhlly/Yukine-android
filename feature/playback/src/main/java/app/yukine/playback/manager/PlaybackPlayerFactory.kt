package app.yukine.playback.manager

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
internal class PlaybackPlayerFactory(
    private val context: Context,
    private val audioProcessor: AudioProcessor
) {
    companion object {
        private const val TAG = "PlaybackPlayerFactory"
        private const val STREAMING_MIN_BUFFER_MS = 15000
        private const val STREAMING_MAX_BUFFER_MS = 120000
        private const val STREAMING_BUFFER_FOR_PLAYBACK_MS = 750
        private const val STREAMING_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1500
        private const val STREAMING_BACK_BUFFER_MS = 15000
    }

    fun createPlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(audioProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        STREAMING_MIN_BUFFER_MS,
                        STREAMING_MAX_BUFFER_MS,
                        STREAMING_BUFFER_FOR_PLAYBACK_MS,
                        STREAMING_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(STREAMING_BACK_BUFFER_MS, true)
                    .build()
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun releasePlayer(
        player: ExoPlayer?,
        playerListener: Player.Listener,
        audioEffectManager: PlaybackAudioEffectManager,
        sessionReleaser: Runnable,
        cacheReleaser: Runnable
    ) {
        sessionReleaser.run()
        audioEffectManager.release()
        if (player == null) {
            cacheReleaser.run()
            return
        }
        try {
            player.removeListener(playerListener)
            player.stop()
        } catch (_: IllegalStateException) {
        }
        player.release()
        cacheReleaser.run()
    }
}
