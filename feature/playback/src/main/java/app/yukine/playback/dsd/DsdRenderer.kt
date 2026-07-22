package app.yukine.playback.dsd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import app.yukine.playback.usb.UsbExclusiveAudioSink

/** Media3 renderer that sends raw DSD access units only to the USB DoP/native output. */
@UnstableApi
internal class DsdRenderer(
    private val usbSink: UsbExclusiveAudioSink
) : BaseRenderer(C.TRACK_TYPE_AUDIO) {
    private val inputBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    private var ended = false
    private var pendingBuffer = false
    private var currentFormat: Format? = null

    override fun getName(): String = "EchoDsdRenderer"

    override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
        if (format.sampleMimeType == MIME_AUDIO_DSD) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE
    )

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (ended) return
        if (pendingBuffer) {
            if (!usbSink.queueDsdAccessUnit(inputBuffer.data ?: return)) return
            pendingBuffer = false
            inputBuffer.clear()
        }
        val result = readSource(formatHolder, inputBuffer, 0)
        when (result) {
            C.RESULT_FORMAT_READ -> {
                val format = formatHolder.format ?: return
                if (currentFormat != format) {
                    configure(format)
                }
            }
            C.RESULT_BUFFER_READ -> {
                if (inputBuffer.isEndOfStream) {
                    ended = true
                    return
                }
                inputBuffer.flip()
                if (!usbSink.queueDsdAccessUnit(inputBuffer.data ?: return)) {
                    pendingBuffer = true
                } else {
                    inputBuffer.clear()
                }
            }
        }
    }

    override fun isReady(): Boolean = pendingBuffer || isSourceReady
    override fun isEnded(): Boolean = ended

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        formats.firstOrNull { it.sampleMimeType == MIME_AUDIO_DSD }?.let(::configure)
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, resetStream: Boolean) {
        ended = false
        pendingBuffer = false
        inputBuffer.clear()
        usbSink.flushDsd()
    }

    override fun onDisabled() {
        ended = false
        pendingBuffer = false
        currentFormat = null
        inputBuffer.clear()
        usbSink.reset()
    }

    private fun configure(format: Format) {
        try {
            usbSink.configureDsd(format)
            currentFormat = format
        } catch (failure: RuntimeException) {
            throw createRendererException(
                failure,
                format,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
            )
        }
    }
}
