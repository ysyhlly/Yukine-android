package app.yukine.playback.usb

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB Exclusive [AudioSink]: force-claims all USB audio interfaces and writes
 * PCM directly to the USB DAC endpoint, completely bypassing AudioFlinger.
 *
 * Endpoint discovery uses 3 phases:
 * 1. Standard API scan (alt 0 endpoints)
 * 2. SET_INTERFACE + re-scan
 * 3. Raw descriptor parsing + reflection-created UsbEndpoint (alt 1)
 *
 * If all phases fail, falls back to [DefaultAudioSink] so playback continues.
 */
@UnstableApi
internal class UsbExclusiveAudioSink(
    private val deviceManager: UsbAudioDeviceManager
) : AudioSink {

    companion object {
        private const val TAG = "UsbExclusiveAudioSink"
    }

    private var writer: UsbPcmWriter? = null
    private var configured = false
    private var playing = false
    private var inputEnded = false
    private var startPositionUs = 0L
    private var listener: AudioSink.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fallbackSink: DefaultAudioSink? = null

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        fallbackSink?.setListener(listener)
    }

    override fun configure(format: Format, specifiedBufferSize: Int, tunnelingAudioSessionId: IntArray?) {
        if (tryConfigureDirectUsb(format)) return
        // Direct USB failed — fallback to system route (audio still plays).
        Log.w(TAG, "Direct USB failed, using system audio route")
        deviceManager.closeConnection()
        val sink = DefaultAudioSink.Builder()
            .setAudioProcessors(emptyArray())
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            .build()
        listener?.let { sink.setListener(it) }
        sink.configure(format, specifiedBufferSize, tunnelingAudioSessionId)
        fallbackSink = sink
    }

    private fun tryConfigureDirectUsb(format: Format): Boolean {
        return try {
            val conn = deviceManager.openConnection() ?: return false
            val endpoint = deviceManager.findAudioEndpoint()
            if (endpoint == null) {
                deviceManager.closeConnection()
                return false
            }

            val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 48000
            val channelCount = format.channelCount.takeIf { it > 0 } ?: 2
            val config = UsbAudioStreamConfig(
                endpointAddress = endpoint.address,
                maxPacketSize = endpoint.maxPacketSize,
                sampleRateHz = sampleRate,
                bitDepth = 16,
                channelCount = channelCount,
                interfaceNumber = 0,
                alternateSetting = 1
            )

            writer = UsbPcmWriter(config, endpoint) {
                Log.e(TAG, "USB write error")
                mainHandler.post {
                    listener?.onAudioSinkError(IllegalStateException("USB write failed"))
                }
            }
            configured = true
            inputEnded = false
            writer?.start(conn)
            Log.d(TAG, "USB DIRECT ACTIVE: ${sampleRate}Hz/${channelCount}ch, " +
                "ep=0x${Integer.toHexString(endpoint.address)}, pkt=${endpoint.maxPacketSize}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "USB direct config error: ${e.message}")
            false
        }
    }

    override fun play() { playing = true; fallbackSink?.play() }
    override fun pause() { playing = false; fallbackSink?.pause() }

    override fun setPlaybackParameters(p: PlaybackParameters) { fallbackSink?.setPlaybackParameters(p) }
    override fun getPlaybackParameters(): PlaybackParameters =
        fallbackSink?.playbackParameters ?: PlaybackParameters.DEFAULT

    override fun setAudioSessionId(id: Int) { fallbackSink?.setAudioSessionId(id) }
    override fun setAuxEffectInfo(info: AuxEffectInfo) { fallbackSink?.setAuxEffectInfo(info) }
    override fun enableTunnelingV21() { fallbackSink?.enableTunnelingV21() }
    override fun disableTunneling() { fallbackSink?.disableTunneling() }
    override fun setVolume(volume: Float) { fallbackSink?.setVolume(volume) }
    override fun setSkipSilenceEnabled(enabled: Boolean) { fallbackSink?.setSkipSilenceEnabled(enabled) }
    override fun getSkipSilenceEnabled(): Boolean = fallbackSink?.getSkipSilenceEnabled() ?: false
    override fun setAudioAttributes(attrs: AudioAttributes) { fallbackSink?.setAudioAttributes(attrs) }
    override fun getAudioAttributes(): AudioAttributes? = fallbackSink?.audioAttributes

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        fallbackSink?.let { return it.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount) }
        if (!playing) return false
        val w = writer ?: return false
        val remaining = buffer.remaining()
        if (remaining <= 0) return true
        val pcmData = ByteArray(remaining)
        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).get(pcmData)
        buffer.position(buffer.limit())
        return w.queueBuffer(pcmData)
    }

    override fun handleDiscontinuity() { fallbackSink?.handleDiscontinuity(); writer?.resetPosition() }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        fallbackSink?.let { return it.getCurrentPositionUs(sourceEnded) }
        val w = writer ?: return startPositionUs
        return startPositionUs + w.currentPositionUs
    }

    override fun isEnded(): Boolean {
        fallbackSink?.let { return it.isEnded }
        return inputEnded && (writer?.queuedBufferCount() ?: 0) == 0
    }

    override fun hasPendingData(): Boolean {
        fallbackSink?.let { return it.hasPendingData() }
        return (writer?.queuedBufferCount() ?: 0) > 0
    }

    override fun playToEndOfStream() { inputEnded = true; fallbackSink?.playToEndOfStream() }
    override fun flush() { writer?.resetPosition(); inputEnded = false; fallbackSink?.flush() }

    override fun reset() {
        writer?.stop()
        writer = null
        configured = false
        playing = false
        inputEnded = false
        startPositionUs = 0L
        fallbackSink?.reset()
        fallbackSink = null
        deviceManager.closeConnection()
    }

    override fun supportsFormat(format: Format): Boolean = true
    override fun getFormatSupport(format: Format): Int = 2
    override fun getAudioTrackBufferSizeUs(): Long = fallbackSink?.audioTrackBufferSizeUs ?: 0L
}
