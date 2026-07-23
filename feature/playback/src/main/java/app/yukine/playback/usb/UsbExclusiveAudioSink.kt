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

internal object UsbSessionOpenRetryPolicy {
    fun shouldRetry(reason: AudioFallbackReason): Boolean = when (reason) {
        AudioFallbackReason.CLOCK_NEGOTIATION_FAILED,
        AudioFallbackReason.SESSION_RECONFIGURE_FAILED -> true
        else -> false
    }
}

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
    private val allowDsd: Boolean = false,
    private val allowUac2PcmRateMismatch: Boolean = false
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

    constructor(
        deviceManager: UsbAudioDeviceManager,
        allowDsd: Boolean,
        allowUac2PcmRateMismatch: Boolean,
        outputListener: UsbAudioOutputListener
    ) : this(
        deviceManager,
        DefaultUsbPcmTransportFactory,
        outputListener,
        allowDsd,
        allowUac2PcmRateMismatch
    )

    companion object {
        private const val TAG = "UsbExclusiveAudioSink"
        private const val METRIC_PUBLISH_INTERVAL_MS = 250L
        private const val METRIC_LOG_INTERVAL_MS = 1_000L
        private const val SESSION_OPEN_ATTEMPTS = 2
        private val SUPPORTED_PCM_SAMPLE_RATES = setOf(
            44_100,
            48_000,
            88_200,
            96_000,
            176_400,
            192_000
        )
    }

    private val sessionOwner = UsbPcmSessionOwner(deviceManager::closeConnection)
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
        val request = pcmRequest(format)
        val deviceId = deviceManager.activeDevice?.device?.deviceId ?: -1
        if (sessionOwner.matchesRequest(
                deviceId,
                request.sampleRateHz,
                request.channelCount,
                request.bitDepth
            )
        ) {
            DiagnosticLog.d(
                TAG,
                "Keeping compatible USB PCM session at ${request.sampleRateHz} Hz"
            )
            return
        }

        val previousRate = activeSnapshot.sampleRateHz
        activeSnapshot = AudioOutputSnapshot.transition(
            AudioTransport.USB_PCM,
            deviceManager.activeDevice?.deviceName.orEmpty(),
            previousRate,
            request.sampleRateHz,
            request.bitDepth,
            request.channelCount
        )
        emit(activeSnapshot)

        var lastFailure: Exception? = null
        var lastReason = AudioFallbackReason.SESSION_RECONFIGURE_FAILED
        for (attempt in 1..SESSION_OPEN_ATTEMPTS) {
            val generation = sessionOwner.beginTransition()
            configured = false
            resetPcmSessionState()
            try {
                configureDirectUsb(request, generation)
                return
            } catch (failure: Exception) {
                lastFailure = failure
                lastReason = configurationFailureReason(failure)
                DiagnosticLog.w(
                    TAG,
                    "USB PCM session attempt $attempt/$SESSION_OPEN_ATTEMPTS failed: " +
                        failure.message.orEmpty()
                )
                if (!UsbSessionOpenRetryPolicy.shouldRetry(lastReason)) {
                    break
                }
            }
        }

        val message = lastFailure?.message.orEmpty().ifBlank {
            "USB PCM session reconfiguration failed"
        }
        reportConfigurationFailure(lastReason, message)
        throw AudioSink.ConfigurationException(message, format)
    }

    private fun configureDirectUsb(request: PcmRequest, generation: Long) {
            val sampleRate = request.sampleRateHz
            val channelCount = request.channelCount
            val bitDepth = request.bitDepth
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
                throw UsbConfigurationFailure(
                    AudioFallbackReason.FORMAT_UNSUPPORTED,
                    "Unsupported USB PCM container: source=${sourceSampleBytes}B, " +
                        "endpoint=${usbSampleBytes}B"
                )
            }
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
                synchronizationType = selection.synchronizationType,
                allowUac2PcmRateMismatch = allowUac2PcmRateMismatch
            )

            DiagnosticLog.d(
                TAG,
                "USB PCM negotiating: ${sampleRate}Hz/${channelCount}ch/${bitDepth}bit, " +
                    "uac=${selection.audioClassVersion}, controlIf=${selection.controlInterfaceNumber}, " +
                    "streamIf=${selection.interfaceNumber}, alt=${selection.alternateSetting}, " +
                    "endpoint=0x${Integer.toHexString(endpoint.address)}, " +
                    "clockSources=${selection.clockSourceEntityIds.contentToString()}, " +
                    "frequencyControls=${selection.clockSourceFrequencyControls.contentToString()}, " +
                    "clockSelector=${selection.clockSelectorEntityId}, " +
                    "selectorControl=${selection.clockSelectorControl}, " +
                    "endpointFrequencyControl=${selection.sampleFrequencyControl}, " +
                    "allowUac2PcmRateMismatch=$allowUac2PcmRateMismatch"
            )
            val transport = transportFactory.create(conn, endpoint, config)
            val candidateWriter = createWriter(config, transport, generation)
            val key = UsbPcmSessionKey(
                deviceId = deviceManager.activeDevice?.device?.deviceId ?: -1,
                sampleRateHz = sampleRate,
                channelCount = channelCount,
                sourceBitDepth = bitDepth,
                usbSubslotBytes = usbSampleBytes,
                interfaceNumber = selection.interfaceNumber,
                alternateSetting = selection.alternateSetting,
                clockSourceEntityId = selection.clockSourceEntityId
            )
            if (!sessionOwner.install(generation, key, candidateWriter)) {
                candidateWriter.stop()
                throw UsbConfigurationFailure(
                    AudioFallbackReason.SESSION_RECONFIGURE_FAILED,
                    "USB PCM session was superseded during negotiation"
                )
            }
            inputSampleBytes = sourceSampleBytes
            outputSampleBytes = usbSampleBytes
            clearPendingPcm()
            configured = true
            inputEnded = false
            dsdMetadata = null
            startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
            candidateWriter.setPlaybackEnabled(playing)
            candidateWriter.start()
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
        val generation = sessionOwner.beginTransition()
        configured = false
        resetPcmSessionState()
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
                synchronizationType = selection.synchronizationType,
                allowUac2PcmRateMismatch = false
            )
            val requiredPayload = ((dopSampleRate.toLong() * config.bytesPerFrame *
                serviceIntervalUs(config) + 999_999L) / 1_000_000L).toInt()
            if (requiredPayload > config.maximumPayloadBytes) throw UsbConfigurationFailure(
                AudioFallbackReason.DOP_UNSUPPORTED,
                "DoP requires $requiredPayload bytes per interval; endpoint supports ${config.maximumPayloadBytes}"
            )
            val transport = transportFactory.create(connection, endpoint, config)
            val candidateWriter = createWriter(config, transport, generation)
            val key = UsbPcmSessionKey(
                deviceId = deviceManager.activeDevice?.device?.deviceId ?: -1,
                sampleRateHz = dopSampleRate,
                channelCount = channelCount,
                sourceBitDepth = 24,
                usbSubslotBytes = 3,
                interfaceNumber = selection.interfaceNumber,
                alternateSetting = selection.alternateSetting,
                clockSourceEntityId = selection.clockSourceEntityId
            )
            if (!sessionOwner.install(generation, key, candidateWriter)) {
                candidateWriter.stop()
                throw UsbConfigurationFailure(
                    AudioFallbackReason.SESSION_RECONFIGURE_FAILED,
                    "USB DoP session was superseded during negotiation"
                )
            }
            configured = true
            inputEnded = false
            dsdMetadata = metadata
            dopPacker.reset()
            candidateWriter.setPlaybackEnabled(playing)
            candidateWriter.start()
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
        return sessionOwner.writer()?.queueBuffer(dopPacker.pack(channels)) == true
    }

    fun flushDsd() {
        dopPacker.reset()
        sessionOwner.writer()?.resetPosition()
    }

    private fun serviceIntervalUs(config: UsbAudioStreamConfig): Int =
        125 shl (config.interval - 1).coerceIn(0, 7)

    private fun createWriter(
        config: UsbAudioStreamConfig,
        transport: UsbPcmTransport,
        generation: Long
    ): UsbPcmWriter {
        lateinit var candidate: UsbPcmWriter
        candidate = UsbPcmWriter(
            config,
            transport,
            { error -> onWriterFailure(generation, candidate, error) },
            { metrics -> onWriterMetrics(generation, candidate, metrics) }
        )
        return candidate
    }

    private fun onWriterFailure(generation: Long, candidate: UsbPcmWriter, error: String) {
        if (!sessionOwner.detachIfCurrent(generation, candidate)) return
        DiagnosticLog.e(TAG, "USB write error: $error")
        candidate.stop()
        deviceManager.closeConnection()
        configured = false
        resetPcmSessionState()
        emitFailure(AudioFallbackReason.TRANSFER_FAILED, error)
        mainHandler.post { listener?.onAudioSinkError(IllegalStateException(error)) }
    }

    private fun reportConfigurationFailure(reason: AudioFallbackReason, error: String) {
        DiagnosticLog.e(TAG, "USB direct config error [$reason]: $error")
        sessionOwner.closeCurrent()
        configured = false
        resetPcmSessionState()
        emitFailure(reason, error)
    }

    private fun emitFailure(reason: AudioFallbackReason, error: String) {
        emit(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                deviceManager.activeDevice?.deviceName.orEmpty(),
                activeSnapshot.sampleRateHz,
                activeSnapshot.previousSampleRateHz,
                activeSnapshot.requestedSampleRateHz,
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

    private fun onWriterMetrics(
        generation: Long,
        candidate: UsbPcmWriter,
        metrics: UsbPcmWriterMetrics
    ) {
        if (!sessionOwner.isCurrent(generation, candidate)) return
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

    private fun pcmRequest(format: Format): PcmRequest {
        if (!isUsbPcmFormat(format)) {
            throw AudioSink.ConfigurationException(
                "USB PCM sink requires decoded audio/raw PCM, got " +
                    "${format.sampleMimeType}/${format.pcmEncoding}",
                format
            )
        }
        val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 48_000
        val channelCount = format.channelCount.takeIf { it > 0 } ?: 2
        val bitDepth = pcmBitDepth(format)
        if (sampleRate !in SUPPORTED_PCM_SAMPLE_RATES || channelCount !in 1..2 ||
            bitDepth !in setOf(16, 24, 32)
        ) {
            throw AudioSink.ConfigurationException(
                "Unsupported USB PCM request: ${sampleRate}Hz/${channelCount}ch/${bitDepth}bit",
                format
            )
        }
        return PcmRequest(sampleRate, channelCount, bitDepth)
    }

    private fun configurationFailureReason(failure: Exception): AudioFallbackReason {
        if (failure is UsbConfigurationFailure) return failure.reason
        val message = failure.message.orEmpty()
        return when {
            message.contains("native library unavailable", ignoreCase = true) ->
                AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE
            message.contains("clock", ignoreCase = true) ||
                message.contains("sample rate", ignoreCase = true) ||
                message.contains("alternate setting", ignoreCase = true) ||
                message.contains("isochronous transport", ignoreCase = true) ->
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
            else -> AudioFallbackReason.SESSION_RECONFIGURE_FAILED
        }
    }

    override fun play() {
        playing = true
        sessionOwner.writer()?.setPlaybackEnabled(true)
    }

    override fun pause() {
        playing = false
        sessionOwner.writer()?.setPlaybackEnabled(false)
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
        val w = sessionOwner.writer() ?: return false
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
        sessionOwner.writer()?.resetPosition()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (startPositionUs == AudioSink.CURRENT_POSITION_NOT_SET) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val w = sessionOwner.writer() ?: return startPositionUs
        return startPositionUs + w.currentPositionUs
    }

    override fun isEnded(): Boolean {
        return inputEnded && (sessionOwner.writer()?.queuedBufferCount() ?: 0) == 0
    }

    override fun hasPendingData(): Boolean {
        return pendingQueuePayload != null ||
            (sessionOwner.writer()?.queuedBufferCount() ?: 0) > 0
    }

    override fun playToEndOfStream() { inputEnded = true }
    override fun flush() {
        clearPendingPcm()
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        sessionOwner.writer()?.resetPosition()
        inputEnded = false
    }

    override fun reset() {
        sessionOwner.closeCurrent()
        configured = false
        playing = false
        inputEnded = false
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        inputSampleBytes = 0
        outputSampleBytes = 0
        clearPendingPcm()
    }

    override fun supportsFormat(format: Format): Boolean = isUsbPcmFormat(format)
    override fun getFormatSupport(format: Format): Int = if (supportsFormat(format)) 2 else 0
    override fun getAudioTrackBufferSizeUs(): Long = C.TIME_UNSET

    private fun clearPendingPcm() {
        pendingPcmRemainder = byteArrayOf()
        pendingQueuePayload = null
        pendingQueueRemainder = byteArrayOf()
    }

    private fun resetPcmSessionState() {
        inputEnded = false
        startPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        inputSampleBytes = 0
        outputSampleBytes = 0
        clearPendingPcm()
    }

    private class UsbConfigurationFailure(
        val reason: AudioFallbackReason,
        message: String
    ) : Exception(message)

    private data class PcmRequest(
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitDepth: Int
    )

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
