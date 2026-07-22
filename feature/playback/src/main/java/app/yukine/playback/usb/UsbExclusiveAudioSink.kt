package app.yukine.playback.usb

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.yukine.diagnostics.DiagnosticLog
import app.yukine.playback.AudioFallbackReason
import app.yukine.playback.AudioOutputPhase
import app.yukine.playback.AudioOutputSnapshot
import app.yukine.playback.AudioTransport
import app.yukine.playback.dsd.DoPPacker
import app.yukine.playback.dsd.DsdContainer
import app.yukine.playback.dsd.DsdFormatMetadata
import app.yukine.playback.dsd.DsdPayloadDecoder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB Exclusive [AudioSink]: force-claims all USB audio interfaces and writes
 * PCM directly to the USB DAC endpoint, completely bypassing AudioFlinger.
 *
 * Endpoint discovery parses every raw AudioStreaming alternate setting, matches its declared
 * PCM wire format, and reuses the corresponding public endpoint projection when available.
 *
 * Negotiation failures are reported to the coordinator and thrown to Media3. This sink never
 * hides a failed USB session behind a system [android.media.AudioTrack] route.
 */
@UnstableApi
internal class UsbExclusiveAudioSink @JvmOverloads constructor(
    private val deviceManager: UsbAudioDeviceManager,
    private val transportFactory: UsbPcmTransportFactory = DefaultUsbPcmTransportFactory,
    private val outputListener: UsbAudioOutputListener = UsbAudioOutputListener { },
    private val allowDsd: Boolean = false
) : AudioSink {

    constructor(
        deviceManager: UsbAudioDeviceManager,
        outputListener: UsbAudioOutputListener
    ) : this(deviceManager, DefaultUsbPcmTransportFactory, outputListener)

    constructor(
        deviceManager: UsbAudioDeviceManager,
        allowDsd: Boolean,
        outputListener: UsbAudioOutputListener
    ) : this(deviceManager, DefaultUsbPcmTransportFactory, outputListener, allowDsd)

    companion object {
        private const val TAG = "UsbExclusiveAudioSink"
        private const val METRIC_PUBLISH_INTERVAL_MS = 250L
        private const val METRIC_LOG_INTERVAL_MS = 1_000L
    }

    private var writer: UsbPcmWriter? = null
    private var configured = false
    // Media3 configures and prebuffers an AudioSink before it necessarily invokes play().
    // Starting paused here deadlocks that bootstrap once the bounded writer queue fills.
    // An explicit pause() still disables the writer immediately.
    private var playing = true
    private var inputEnded = false
    private var startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
    private var listener: AudioSink.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSnapshot = AudioOutputSnapshot.idle()
    private var lastMetricPublishMs = 0L
    private var lastMetricLogMs = 0L
    private var dsdMetadata: DsdFormatMetadata? = null
    private var inputSampleBytes = 0
    private var outputSampleBytes = 0
    private var pendingPcmRemainder = byteArrayOf()
    private var pendingQueuePayload: ByteArray? = null
    private var pendingQueueRemainder = byteArrayOf()
    private val dopPacker = DoPPacker()

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun configure(format: Format, specifiedBufferSize: Int, tunnelingAudioSessionId: IntArray?) {
        emit(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.NEGOTIATING,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                format.sampleRate.coerceAtLeast(0),
                pcmBitDepth(format),
                format.channelCount.coerceAtLeast(0),
                0,
                AudioFallbackReason.NONE,
                0, 0, 0, 0, 0, 0, 0.0, ""
            )
        )
        try {
            configureDirectUsb(format)
        } catch (failure: UsbConfigurationFailure) {
            reportConfigurationFailure(failure.reason, failure.message.orEmpty())
            throw AudioSink.ConfigurationException(failure.message.orEmpty(), format)
        } catch (failure: Exception) {
            val message = failure.message.orEmpty()
            val reason = if (message.contains("native library unavailable", ignoreCase = true)) {
                AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE
            } else {
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
            }
            reportConfigurationFailure(reason, message)
            throw AudioSink.ConfigurationException(message, format)
        }
    }

    private fun configureDirectUsb(format: Format) {
            if (!isUsbPcmFormat(format)) {
                throw UsbConfigurationFailure(
                    AudioFallbackReason.FORMAT_UNSUPPORTED,
                    "USB PCM sink requires decoded audio/raw PCM, got " +
                        "${format.sampleMimeType}/${format.pcmEncoding}"
                )
            }
            if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                throw UsbConfigurationFailure(
                    AudioFallbackReason.FORMAT_UNSUPPORTED,
                    "Floating-point PCM cannot be sent bit-perfect to an integer USB Audio endpoint"
                )
            }
            val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 48000
            val channelCount = format.channelCount.takeIf { it > 0 } ?: 2
            val bitDepth = pcmBitDepth(format)
            val conn = deviceManager.openConnection() ?: throw UsbConfigurationFailure(
                if (deviceManager.activeDevice == null) AudioFallbackReason.NO_USB_DEVICE
                else AudioFallbackReason.USB_PERMISSION_DENIED,
                if (deviceManager.activeDevice == null) "No USB audio device connected"
                else "USB permission was not granted"
            )
            val selection = deviceManager.findAudioEndpointSelection(
                sampleRate,
                channelCount,
                bitDepth
            )
            if (selection == null) {
                deviceManager.closeConnection()
                throw UsbConfigurationFailure(
                    AudioFallbackReason.NO_COMPATIBLE_ENDPOINT,
                    "No compatible USB Audio streaming endpoint"
                )
            }
            val endpoint = selection.endpoint
            val sourceSampleBytes = (bitDepth + 7) / 8
            val usbSampleBytes = selection.subslotSizeBytes.takeIf { it > 0 }
                ?: sourceSampleBytes
            if (usbSampleBytes !in sourceSampleBytes..4) {
                deviceManager.closeConnection()
                throw UsbConfigurationFailure(
                    AudioFallbackReason.FORMAT_UNSUPPORTED,
                    "Unsupported USB PCM container: source=${sourceSampleBytes}B, " +
                        "endpoint=${usbSampleBytes}B"
                )
            }
            inputSampleBytes = sourceSampleBytes
            outputSampleBytes = usbSampleBytes
            clearPendingPcm()

            val parsedConfig = deviceManager.activeDevice?.streamConfig
            val config = UsbAudioStreamConfig(
                endpointAddress = endpoint.address,
                maxPacketSize = endpoint.maxPacketSize,
                sampleRateHz = sampleRate,
                // Native packet scheduling needs the USB subslot width, not merely the
                // source's valid bit count. Packed 16/24-bit samples are losslessly
                // left-aligned below before entering a wider UAC subslot.
                bitDepth = usbSampleBytes * 8,
                channelCount = channelCount,
                interfaceNumber = selection.interfaceNumber,
                alternateSetting = selection.alternateSetting,
                feedbackEndpointAddress = selection.feedbackEndpointAddress
                    .takeIf { it != 0 } ?: (parsedConfig?.feedbackEndpointAddress ?: 0),
                feedbackMaxPacketSize = selection.feedbackMaxPacketSize,
                endpointType = endpoint.type,
                interval = endpoint.interval,
                audioClassVersion = selection.audioClassVersion,
                controlInterfaceNumber = selection.controlInterfaceNumber,
                clockSourceEntityId = selection.clockSourceEntityId,
                clockSourceEntityIds = selection.clockSourceEntityIds,
                clockSourceFrequencyControls = selection.clockSourceFrequencyControls,
                clockSelectorEntityId = selection.clockSelectorEntityId,
                clockSelectorControl = selection.clockSelectorControl,
                sampleFrequencyControl = selection.sampleFrequencyControl,
                synchronizationType = selection.synchronizationType
            )

            val transport = transportFactory.create(conn, endpoint, config)
            writer = UsbPcmWriter(config, transport, { error ->
                DiagnosticLog.e(TAG, "USB write error: $error")
                emitFailure(AudioFallbackReason.TRANSFER_FAILED, error)
                mainHandler.post {
                    listener?.onAudioSinkError(IllegalStateException(error))
                }
            }, ::onWriterMetrics)
            configured = true
            inputEnded = false
            startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
            writer?.setPlaybackEnabled(playing)
            writer?.start()
            activeSnapshot = AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.ACTIVE,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                sampleRate,
                config.bitDepth,
                channelCount,
                0,
                AudioFallbackReason.NONE,
                0, 0, 0, 0, 0, 0, 0.0, ""
            )
            emit(activeSnapshot)
            DiagnosticLog.d(TAG, "USB DIRECT ACTIVE: ${sampleRate}Hz/${channelCount}ch, " +
                "source=${bitDepth}bit, usbSubslot=${usbSampleBytes}B, " +
                "usbResolution=${selection.bitResolution}, alt=${selection.alternateSetting}, " +
                "ep=0x${Integer.toHexString(endpoint.address)}, type=${endpoint.type}, " +
                "pkt=${endpoint.maxPacketSize}, interval=${endpoint.interval}")
    }

    fun configureDsd(format: Format) {
        require(allowDsd) { "DSD requires both Bit-Perfect and USB exclusive output" }
        val metadata = format.initializationData.firstOrNull()?.let(DsdFormatMetadata::decode)
            ?: throw IllegalArgumentException("Missing DSD container metadata")
        require(!metadata.dstCompressed) { "DST-compressed DSDIFF is not supported" }
        val dsdRate = format.sampleRate / 44_100
        require(dsdRate in setOf(64, 128, 256, 512)) { "Unsupported DSD rate: $dsdRate" }
        val dopSampleRate = format.sampleRate / 16
        val channelCount = format.channelCount.coerceIn(1, 2)
        emit(
            AudioOutputSnapshot(
                AudioTransport.USB_DOP,
                AudioOutputPhase.NEGOTIATING,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                dopSampleRate,
                24,
                channelCount,
                dsdRate,
                AudioFallbackReason.NONE,
                0, 0, 0, 0, 0, 0, 0.0, ""
            )
        )
        try {
            val connection = deviceManager.openConnection() ?: throw UsbConfigurationFailure(
                if (deviceManager.activeDevice == null) AudioFallbackReason.NO_USB_DEVICE
                else AudioFallbackReason.USB_PERMISSION_DENIED,
                "Unable to open USB DAC for DSD"
            )
            val selection = deviceManager.findAudioEndpointSelection(
                dopSampleRate,
                channelCount,
                24
            ) ?: throw UsbConfigurationFailure(
                AudioFallbackReason.NO_COMPATIBLE_ENDPOINT,
                "No compatible DoP endpoint"
            )
            if (selection.subslotSizeBytes !in setOf(0, 3) ||
                selection.bitResolution !in setOf(0, 24)
            ) throw UsbConfigurationFailure(
                AudioFallbackReason.DOP_UNSUPPORTED,
                "DoP requires a 24-bit/3-byte PCM alternate setting"
            )
            val endpoint = selection.endpoint
            val config = UsbAudioStreamConfig(
                endpointAddress = endpoint.address,
                maxPacketSize = endpoint.maxPacketSize,
                sampleRateHz = dopSampleRate,
                bitDepth = 24,
                channelCount = channelCount,
                interfaceNumber = selection.interfaceNumber,
                alternateSetting = selection.alternateSetting,
                feedbackEndpointAddress = selection.feedbackEndpointAddress.takeIf { it != 0 }
                    ?: (deviceManager.activeDevice?.streamConfig?.feedbackEndpointAddress ?: 0),
                feedbackMaxPacketSize = selection.feedbackMaxPacketSize,
                endpointType = endpoint.type,
                interval = endpoint.interval,
                audioClassVersion = selection.audioClassVersion,
                controlInterfaceNumber = selection.controlInterfaceNumber,
                clockSourceEntityId = selection.clockSourceEntityId,
                clockSourceEntityIds = selection.clockSourceEntityIds,
                clockSourceFrequencyControls = selection.clockSourceFrequencyControls,
                clockSelectorEntityId = selection.clockSelectorEntityId,
                clockSelectorControl = selection.clockSelectorControl,
                sampleFrequencyControl = selection.sampleFrequencyControl,
                synchronizationType = selection.synchronizationType
            )
            val requiredPayload = ((dopSampleRate.toLong() * config.bytesPerFrame *
                serviceIntervalUs(config) + 999_999L) / 1_000_000L).toInt()
            if (requiredPayload > config.maximumPayloadBytes) throw UsbConfigurationFailure(
                AudioFallbackReason.DOP_UNSUPPORTED,
                "DoP requires $requiredPayload bytes per interval; endpoint supports ${config.maximumPayloadBytes}"
            )
            val transport = transportFactory.create(connection, endpoint, config)
            writer = createWriter(config, transport)
            configured = true
            inputEnded = false
            dsdMetadata = metadata
            dopPacker.reset()
            writer?.setPlaybackEnabled(playing)
            writer?.start()
            activeSnapshot = AudioOutputSnapshot(
                AudioTransport.USB_DOP,
                AudioOutputPhase.ACTIVE,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                dopSampleRate,
                24,
                channelCount,
                dsdRate,
                AudioFallbackReason.NONE,
                0, 0, 0, 0, 0, 0, 0.0, ""
            )
            emit(activeSnapshot)
        } catch (failure: UsbConfigurationFailure) {
            reportConfigurationFailure(failure.reason, failure.message.orEmpty())
            throw IllegalStateException(failure.message, failure)
        } catch (failure: Exception) {
            val reason = if (failure.message.orEmpty().contains("native library", true)) {
                AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE
            } else {
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
            }
            reportConfigurationFailure(reason, failure.message.orEmpty())
            throw IllegalStateException(failure.message, failure)
        }
    }

    fun queueDsdAccessUnit(buffer: ByteBuffer): Boolean {
        val metadata = dsdMetadata ?: return false
        val channelCount = activeSnapshot.channelCount
        val payload = ByteArray(buffer.remaining())
        buffer.duplicate().get(payload)
        val channels = DsdPayloadDecoder.toCanonicalChannels(payload, channelCount, metadata)
        return writer?.queueBuffer(dopPacker.pack(channels)) == true
    }

    fun flushDsd() {
        dopPacker.reset()
        writer?.resetPosition()
    }

    private fun serviceIntervalUs(config: UsbAudioStreamConfig): Int =
        125 shl (config.interval - 1).coerceIn(0, 7)

    private fun createWriter(config: UsbAudioStreamConfig, transport: UsbPcmTransport): UsbPcmWriter =
        UsbPcmWriter(config, transport, { error ->
            DiagnosticLog.e(TAG, "USB write error: $error")
            emitFailure(AudioFallbackReason.TRANSFER_FAILED, error)
            mainHandler.post { listener?.onAudioSinkError(IllegalStateException(error)) }
        }, ::onWriterMetrics)

    private fun reportConfigurationFailure(reason: AudioFallbackReason, error: String) {
        DiagnosticLog.e(TAG, "USB direct config error [$reason]: $error")
        writer?.stop()
        writer = null
        deviceManager.closeConnection()
        emitFailure(reason, error)
    }

    private fun emitFailure(reason: AudioFallbackReason, error: String) {
        emit(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                activeSnapshot.sampleRateHz,
                activeSnapshot.bitDepth,
                activeSnapshot.channelCount,
                0,
                reason,
                activeSnapshot.queueDepth,
                activeSnapshot.submittedPackets,
                activeSnapshot.completedPackets,
                activeSnapshot.failedPackets,
                activeSnapshot.underruns,
                activeSnapshot.framesWritten,
                activeSnapshot.feedbackRateHz,
                error
            )
        )
    }

    private fun onWriterMetrics(metrics: UsbPcmWriterMetrics) {
        activeSnapshot = activeSnapshot.withMetrics(
            metrics.queueDepth,
            metrics.submittedPackets,
            metrics.completedPackets,
            metrics.failedPackets,
            metrics.underruns,
            metrics.framesWritten,
            metrics.feedbackRateHz,
            metrics.lastError
        )
        val now = SystemClock.elapsedRealtime()
        if (now - lastMetricPublishMs >= METRIC_PUBLISH_INTERVAL_MS) {
            lastMetricPublishMs = now
            emit(activeSnapshot)
        }
        if (now - lastMetricLogMs >= METRIC_LOG_INTERVAL_MS) {
            lastMetricLogMs = now
            DiagnosticLog.d(
                TAG,
                "USB metrics: queue=${metrics.queueDepth}, submitted=${metrics.submittedPackets}, " +
                    "completed=${metrics.completedPackets}, failed=${metrics.failedPackets}, " +
                    "underruns=${metrics.underruns}, frames=${metrics.framesWritten}, " +
                    "feedback=${metrics.feedbackRateHz}, error=${metrics.lastError}"
            )
        }
    }

    private fun emit(snapshot: AudioOutputSnapshot) {
        outputListener.onAudioOutputSnapshot(snapshot)
    }

    private fun pcmBitDepth(format: Format): Int = when (format.pcmEncoding) {
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    override fun play() {
        playing = true
        writer?.setPlaybackEnabled(true)
    }

    override fun pause() {
        playing = false
        writer?.setPlaybackEnabled(false)
    }

    override fun setPlaybackParameters(p: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun setAudioSessionId(id: Int) = Unit
    override fun setAuxEffectInfo(info: AuxEffectInfo) = Unit
    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit
    override fun setVolume(volume: Float) = Unit
    override fun setSkipSilenceEnabled(enabled: Boolean) = Unit
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun setAudioAttributes(attrs: AudioAttributes) = Unit
    override fun getAudioAttributes(): AudioAttributes? = null

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val w = writer ?: return false
        if (startPositionUs == AudioSink.CURRENT_POSITION_NOT_SET) {
            // MediaCodecAudioRenderer expects the sink position to remain on the media timeline
            // after a resume or seek. Starting every USB session at zero makes the player jump
            // backwards and eventually enter a permanent buffering state.
            startPositionUs = presentationTimeUs.coerceAtLeast(0L)
        }
        pendingQueuePayload?.let { payload ->
            if (!w.queueBuffer(payload)) return false
            pendingPcmRemainder = pendingQueueRemainder
            pendingQueuePayload = null
            pendingQueueRemainder = byteArrayOf()
            buffer.position(buffer.limit())
            return true
        }
        val remaining = buffer.remaining()
        if (remaining <= 0) return true
        val pcmData = ByteArray(remaining)
        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).get(pcmData)
        val candidate = repackPcmChunk(
            pcmData,
            pendingPcmRemainder,
            inputSampleBytes,
            outputSampleBytes
        )
        if (candidate.payload.isEmpty()) {
            pendingPcmRemainder = candidate.remainder
            buffer.position(buffer.limit())
            return true
        }
        val queued = w.queueBuffer(candidate.payload)
        if (queued) {
            pendingPcmRemainder = candidate.remainder
            buffer.position(buffer.limit())
        } else {
            // Media3 retries the exact same input buffer in a tight loop while the downstream
            // queue is full. Keep the transformed bytes so retries do not allocate and repack
            // tens of megabytes per second, starving both the decoder and USB writer threads.
            pendingQueuePayload = candidate.payload
            pendingQueueRemainder = candidate.remainder
        }
        return queued
    }

    override fun handleDiscontinuity() {
        clearPendingPcm()
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        writer?.resetPosition()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (startPositionUs == AudioSink.CURRENT_POSITION_NOT_SET) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val w = writer ?: return startPositionUs
        return startPositionUs + w.currentPositionUs
    }

    override fun isEnded(): Boolean {
        return inputEnded && (writer?.queuedBufferCount() ?: 0) == 0
    }

    override fun hasPendingData(): Boolean {
        return pendingQueuePayload != null || (writer?.queuedBufferCount() ?: 0) > 0
    }

    override fun playToEndOfStream() { inputEnded = true }
    override fun flush() {
        clearPendingPcm()
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        writer?.resetPosition()
        inputEnded = false
    }

    override fun reset() {
        writer?.stop()
        writer = null
        configured = false
        playing = false
        inputEnded = false
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        inputSampleBytes = 0
        outputSampleBytes = 0
        clearPendingPcm()
        deviceManager.closeConnection()
    }

    override fun supportsFormat(format: Format): Boolean = isUsbPcmFormat(format)
    override fun getFormatSupport(format: Format): Int = if (supportsFormat(format)) 2 else 0
    override fun getAudioTrackBufferSizeUs(): Long = C.TIME_UNSET

    private fun clearPendingPcm() {
        pendingPcmRemainder = byteArrayOf()
        pendingQueuePayload = null
        pendingQueueRemainder = byteArrayOf()
    }

    private class UsbConfigurationFailure(
        val reason: AudioFallbackReason,
        message: String
    ) : Exception(message)

}

/** Losslessly left-aligns signed little-endian PCM into a wider USB Audio subslot. */
internal fun repackPcmSamples(
    source: ByteArray,
    sourceSampleBytes: Int,
    targetSampleBytes: Int
): ByteArray {
    require(sourceSampleBytes in 1..4)
    require(targetSampleBytes in sourceSampleBytes..4)
    if (sourceSampleBytes == targetSampleBytes) return source
    require(source.size % sourceSampleBytes == 0) {
        "PCM buffer is not aligned to its sample width"
    }
    val sampleCount = source.size / sourceSampleBytes
    val paddingBytes = targetSampleBytes - sourceSampleBytes
    val output = ByteArray(sampleCount * targetSampleBytes)
    var sourceOffset = 0
    var targetOffset = 0
    repeat(sampleCount) {
        source.copyInto(
            output,
            destinationOffset = targetOffset + paddingBytes,
            startIndex = sourceOffset,
            endIndex = sourceOffset + sourceSampleBytes
        )
        sourceOffset += sourceSampleBytes
        targetOffset += targetSampleBytes
    }
    return output
}

internal fun isUsbPcmFormat(format: Format): Boolean =
    format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding in setOf(
        C.ENCODING_PCM_16BIT,
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_32BIT
    )

internal data class PcmRepackChunk(
    val payload: ByteArray,
    val remainder: ByteArray
)

/**
 * Converts only complete samples and carries a split trailing sample into the next Media3
 * buffer. AudioSink input buffers are not guaranteed to end on a sample boundary.
 */
internal fun repackPcmChunk(
    source: ByteArray,
    pendingRemainder: ByteArray,
    sourceSampleBytes: Int,
    targetSampleBytes: Int
): PcmRepackChunk {
    require(sourceSampleBytes in 1..4)
    require(targetSampleBytes in sourceSampleBytes..4)
    val combined = if (pendingRemainder.isEmpty()) {
        source
    } else {
        ByteArray(pendingRemainder.size + source.size).also { bytes ->
            pendingRemainder.copyInto(bytes)
            source.copyInto(bytes, destinationOffset = pendingRemainder.size)
        }
    }
    if (sourceSampleBytes == targetSampleBytes) {
        return PcmRepackChunk(combined, byteArrayOf())
    }
    val completeSize = combined.size - (combined.size % sourceSampleBytes)
    val completeSamples = combined.copyOfRange(0, completeSize)
    val remainder = combined.copyOfRange(completeSize, combined.size)
    return PcmRepackChunk(
        repackPcmSamples(completeSamples, sourceSampleBytes, targetSampleBytes),
        remainder
    )
}

internal fun interface UsbAudioOutputListener {
    fun onAudioOutputSnapshot(snapshot: AudioOutputSnapshot)
}
