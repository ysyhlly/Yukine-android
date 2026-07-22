package app.yukine.playback.manager

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import android.os.Handler
import app.yukine.playback.dsd.DsdRenderer
import app.yukine.playback.usb.UsbExclusiveAudioSink

/**
 * Audio output mode for the playback pipeline.
 *
 * - [STANDARD]: Default path with AudioProcessors (EQ, bass analysis, etc.)
 * - [HARDWARE_OFFLOAD]: Compressed bitstream sent directly to hardware DSP, bypassing Android SRC.
 * - [DIRECT_PCM]: Uncompressed PCM output with no AudioProcessors, closest to bit-perfect
 *   when hardware offload is unavailable.
 * - [USB_EXCLUSIVE]: PCM output directly to USB DAC via USB Host API, completely bypassing
 *   AudioFlinger and system SRC for true bit-perfect output.
 */
internal enum class AudioOutputMode { STANDARD, HARDWARE_OFFLOAD, DIRECT_PCM, USB_EXCLUSIVE }

@UnstableApi
internal class PlaybackPlayerFactory(
    private val context: Context,
    private val audioProcessor: AudioProcessor,
    private val audioOutputMode: AudioOutputMode = AudioOutputMode.STANDARD,
    private val usbAudioSink: UsbExclusiveAudioSink? = null
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
            override fun createRenderers(
                eventHandler: Handler,
                videoRendererEventListener: VideoRendererEventListener,
                audioRendererEventListener: AudioRendererEventListener,
                textRendererOutput: TextOutput,
                metadataRendererOutput: MetadataOutput
            ): Array<Renderer> {
                val defaults = super.createRenderers(
                    eventHandler,
                    videoRendererEventListener,
                    audioRendererEventListener,
                    textRendererOutput,
                    metadataRendererOutput
                )
                val dsdSink = usbAudioSink
                return if (audioOutputMode == AudioOutputMode.USB_EXCLUSIVE && dsdSink != null) {
                    arrayOf(DsdRenderer(dsdSink), *defaults)
                } else {
                    defaults
                }
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return when (audioOutputMode) {
                    AudioOutputMode.HARDWARE_OFFLOAD -> {
                        // Bit-Perfect mode: enable hardware offload to bypass Android SRC.
                        // No AudioProcessors are injected so the raw bitstream reaches the
                        // hardware DSP directly without software resampling.
                        val audioSink = DefaultAudioSink.Builder(context)
                            .setAudioProcessors(emptyArray())
                            .setEnableFloatOutput(true)
                            .setEnableAudioTrackPlaybackParams(false)
                            .build()
                        audioSink.setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED)
                        audioSink
                    }
                    AudioOutputMode.DIRECT_PCM -> {
                        // Direct PCM path: no AudioProcessors, no offload.
                        // AudioTrack outputs raw PCM at the source sample rate,
                        // bypassing software mixing/resampling as much as possible.
                        DefaultAudioSink.Builder(context)
                            .setAudioProcessors(emptyArray())
                            .setEnableFloatOutput(true)
                            .setEnableAudioTrackPlaybackParams(false)
                            .build()
                    }
                    AudioOutputMode.STANDARD -> {
                        DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(audioProcessor))
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                    }
                    AudioOutputMode.USB_EXCLUSIVE -> {
                        // USB Exclusive: PCM directly to USB DAC, bypassing AudioFlinger entirely.
                        usbAudioSink ?: throw IllegalStateException(
                            "USB_EXCLUSIVE mode requires a UsbExclusiveAudioSink instance"
                        )
                    }
                }
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
