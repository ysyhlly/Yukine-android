package app.yukine.playback.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import app.yukine.diagnostics.DiagnosticLog
import java.lang.reflect.Constructor

/**
 * Parses raw USB configuration descriptors to find audio streaming endpoints
 * on alternate setting 1, which Android's standard UsbInterface API does not expose.
 *
 * Uses reflection only to represent an alternate-setting endpoint to Kotlin. The selected
 * interface and endpoint are validated and activated by the native libusb session.
 */
internal object UsbRawDescriptorParser {

    private const val TAG = "UsbRawDescriptorParser"

    // USB descriptor types
    private const val DESC_TYPE_CONFIGURATION = 0x02
    private const val DESC_TYPE_INTERFACE = 0x04
    private const val DESC_TYPE_ENDPOINT = 0x05
    private const val DESC_TYPE_CS_INTERFACE = 0x24
    private const val DESC_TYPE_CS_ENDPOINT = 0x25

    // USB Audio Class
    private const val CLASS_AUDIO = 0x01
    private const val SUBCLASS_AUDIO_STREAMING = 0x02

    /**
     * Parsed endpoint info from raw descriptors.
     */
    data class RawEndpointInfo(
        val address: Int,
        val attributes: Int,
        val maxPacketSize: Int,
        val interval: Int,
        val interfaceNumber: Int,
        val alternateSetting: Int,
        val synchronizationType: Int,
        val transactionsPerServiceInterval: Int,
        val audioClassVersion: Int,
        val controlInterfaceNumber: Int,
        val clockSourceEntityId: Int,
        val clockSourceEntityIds: IntArray = intArrayOf(clockSourceEntityId).filter { it > 0 }.toIntArray(),
        val clockSourceFrequencyControls: IntArray = IntArray(clockSourceEntityIds.size) { -1 },
        val clockSelectorEntityId: Int = 0,
        val clockSelectorControl: Int = 0,
        val sampleFrequencyControl: Int = -1,
        val feedbackEndpointAddress: Int = 0,
        val feedbackMaxPacketSize: Int = 0,
        val isPcmFormat: Boolean = true,
        val channelCount: Int = 0,
        val subslotSizeBytes: Int = 0,
        val bitResolution: Int = 0,
        val discreteSampleRatesHz: IntArray = intArrayOf(),
        val minSampleRateHz: Int = 0,
        val maxSampleRateHz: Int = 0
    )

    data class ClockSourceInfo(
        val entityId: Int,
        val frequencyControl: Int
    )

    data class ClockSelectorInfo(
        val entityId: Int,
        val sourceEntityIds: IntArray,
        val selectorControl: Int
    )

    data class ClockPath(
        val sourceEntityIds: IntArray = intArrayOf(),
        val sourceFrequencyControls: IntArray = intArrayOf(),
        val selectorEntityId: Int = 0,
        val selectorControl: Int = 0
    )

    data class AudioTopology(
        val audioClassVersion: Int = 1,
        val controlInterfaceNumber: Int = 0,
        val clockSourceEntityId: Int = 0,
        val clockSources: List<ClockSourceInfo> = emptyList(),
        val clockSelectors: List<ClockSelectorInfo> = emptyList(),
        val terminalClockEntities: Map<Int, Int> = emptyMap()
    ) {
        fun clockPath(terminalLink: Int): ClockPath {
            val referencedEntity = terminalClockEntities[terminalLink]
                ?: clockSelectors.singleOrNull()?.entityId
                ?: clockSources.singleOrNull()?.entityId
                ?: clockSourceEntityId
            val selector = clockSelectors.firstOrNull { it.entityId == referencedEntity }
            val sourceIds = selector?.sourceEntityIds
                ?: intArrayOf(referencedEntity).filter { candidate ->
                    clockSources.any { it.entityId == candidate }
                }.toIntArray()
            return ClockPath(
                sourceEntityIds = sourceIds,
                sourceFrequencyControls = sourceIds.map { sourceId ->
                    clockSources.firstOrNull { it.entityId == sourceId }?.frequencyControl ?: -1
                }.toIntArray(),
                selectorEntityId = selector?.entityId ?: 0,
                selectorControl = selector?.selectorControl ?: 0
            )
        }
    }

    fun readAudioTopology(conn: UsbDeviceConnection): AudioTopology? =
        readConfigurationDescriptor(conn)?.let(::parseTopology)

    /**
     * Reads the raw configuration descriptor and finds the audio streaming OUT endpoint
     * on alternate setting 1.
     *
     * @param conn Open USB device connection.
     * @return RawEndpointInfo or null if not found.
     */
    fun readAudioStreamingEndpoints(conn: UsbDeviceConnection): List<RawEndpointInfo>? =
        readConfigurationDescriptor(conn)?.let(::parseEndpoints)

    fun findAudioStreamingEndpoint(
        conn: UsbDeviceConnection,
        sampleRateHz: Int = 0,
        channelCount: Int = 0,
        bitDepth: Int = 0
    ): RawEndpointInfo? {
        val endpoints = readAudioStreamingEndpoints(conn) ?: return null
        return selectAudioStreamingEndpoint(endpoints, sampleRateHz, channelCount, bitDepth)
    }

    internal fun selectAudioStreamingEndpoint(
        endpoints: List<RawEndpointInfo>,
        sampleRateHz: Int = 0,
        channelCount: Int = 0,
        bitDepth: Int = 0
    ): RawEndpointInfo? {
        val outputCandidates = endpoints.filter(::isAudioOutputEndpoint)
        val hasFormatDescriptors = outputCandidates.any {
            it.channelCount > 0 || it.subslotSizeBytes > 0 || it.bitResolution > 0 ||
                it.discreteSampleRatesHz.isNotEmpty() || it.maxSampleRateHz > 0
        }
        val bytesPerSample = if (bitDepth > 0) (bitDepth + 7) / 8 else 0
        val compatible = outputCandidates.filter { endpoint ->
            val channelsMatch = channelCount <= 0 || endpoint.channelCount <= 0 ||
                endpoint.channelCount == channelCount
            val subslotMatches = bytesPerSample <= 0 || endpoint.subslotSizeBytes <= 0 ||
                endpoint.subslotSizeBytes >= bytesPerSample
            val resolutionMatches = bitDepth <= 0 || endpoint.bitResolution <= 0 ||
                endpoint.bitResolution >= bitDepth
            val rateMatches = sampleRateHz <= 0 || when {
                endpoint.discreteSampleRatesHz.isNotEmpty() ->
                    sampleRateHz in endpoint.discreteSampleRatesHz
                endpoint.maxSampleRateHz > 0 ->
                    sampleRateHz in endpoint.minSampleRateHz..endpoint.maxSampleRateHz
                else -> true
            }
            endpoint.isPcmFormat && channelsMatch && subslotMatches && resolutionMatches && rateMatches
        }

        // If the DAC describes its wire formats, an incompatible request must fail instead of
        // falling through to an arbitrary alternate setting and emitting correctly transferred
        // but incorrectly framed samples (audible as full-scale crackling/noise).
        if (hasFormatDescriptors && compatible.isEmpty()) return null

        val outEndpoint = (if (compatible.isNotEmpty()) compatible else outputCandidates)
            .sortedWith(
                compareByDescending<RawEndpointInfo> {
                    bytesPerSample > 0 && it.subslotSizeBytes == bytesPerSample &&
                        bitDepth > 0 && it.bitResolution == bitDepth
                }
                .thenBy { if (it.subslotSizeBytes > 0) it.subslotSizeBytes else Int.MAX_VALUE }
                .thenBy { it.alternateSetting }
                .thenBy { if ((it.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_ISOC) 0 else 1 }
            ).firstOrNull()

        val feedback = outEndpoint?.let { output ->
            endpoints.firstOrNull { candidate ->
                (candidate.address and 0x80) != 0 &&
                    (candidate.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                    candidate.interfaceNumber == output.interfaceNumber &&
                    candidate.alternateSetting == output.alternateSetting
            }
        }
        val selected = outEndpoint?.copy(
            feedbackEndpointAddress = feedback?.address ?: 0,
            feedbackMaxPacketSize = feedback?.maxPacketSize?.and(0x7ff) ?: 0
        )
        if (selected != null) {
            DiagnosticLog.d(TAG, "Found audio endpoint: addr=0x${Integer.toHexString(selected.address)}, " +
                "maxPkt=${selected.maxPacketSize}, alt=${selected.alternateSetting}, " +
                "channels=${selected.channelCount}, subslot=${selected.subslotSizeBytes}, " +
                "bits=${selected.bitResolution}, " +
                "iface=#${selected.interfaceNumber}, clockSources=" +
                selected.clockSourceEntityIds.joinToString(prefix = "[", postfix = "]") { "0x${it.toString(16)}" } +
                ", selector=0x${selected.clockSelectorEntityId.toString(16)}")
        }
        return selected
    }

    private fun isAudioOutputEndpoint(ep: RawEndpointInfo): Boolean {
        val transferType = ep.attributes and 0x03
        return (ep.address and 0x80) == 0 && ep.alternateSetting > 0 &&
            (transferType == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                transferType == UsbConstants.USB_ENDPOINT_XFER_BULK)
    }

    /**
     * Creates a [UsbEndpoint] object via reflection from raw endpoint info.
     * This bypasses the limitation of Android's API not exposing alt setting 1 endpoints.
     */
    fun createUsbEndpoint(raw: RawEndpointInfo): UsbEndpoint? {
        return try {
            val constructor: Constructor<UsbEndpoint> = UsbEndpoint::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType,  // address
                Int::class.javaPrimitiveType,  // attributes
                Int::class.javaPrimitiveType,  // maxPacketSize
                Int::class.javaPrimitiveType   // interval
            )
            constructor.isAccessible = true
            val endpoint = constructor.newInstance(
                raw.address,
                raw.attributes,
                raw.maxPacketSize,
                raw.interval
            )
            DiagnosticLog.d(TAG, "Created UsbEndpoint via reflection: 0x${Integer.toHexString(raw.address)}")
            endpoint
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "Failed to create UsbEndpoint via reflection: ${e.message}")
            null
        }
    }

    /**
     * Reads the full configuration descriptor via GET_DESCRIPTOR control transfer.
     */
    private fun readConfigurationDescriptor(conn: UsbDeviceConnection): ByteArray? {
        // First read the 9-byte config descriptor header to get total length
        val header = ByteArray(9)
        val headerRead = conn.controlTransfer(
            0x80, // Device-to-Host, Standard, Device
            0x06, // GET_DESCRIPTOR
            0x0200, // Configuration descriptor (type=2, index=0)
            0,
            header, 9, 1000
        )
        if (headerRead < 9) {
            DiagnosticLog.w(TAG, "Failed to read config descriptor header: $headerRead bytes")
            return null
        }

        // wTotalLength is at offset 2-3 (little-endian)
        val totalLength = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        if (totalLength < 9 || totalLength > 4096) {
            DiagnosticLog.w(TAG, "Invalid config descriptor length: $totalLength")
            return null
        }

        // Read the full configuration descriptor
        val fullDesc = ByteArray(totalLength)
        val fullRead = conn.controlTransfer(
            0x80, 0x06, 0x0200, 0,
            fullDesc, totalLength, 1000
        )
        if (fullRead < totalLength) {
            DiagnosticLog.w(TAG, "Incomplete config descriptor: got $fullRead/$totalLength bytes")
            return null
        }

        return fullDesc
    }

    /**
     * Parses raw configuration descriptor bytes to extract endpoint information
     * from Audio Streaming interfaces.
     */
    internal fun parseEndpoints(desc: ByteArray): List<RawEndpointInfo> {
        val results = mutableListOf<RawEndpointInfo>()
        val topology = parseTopology(desc)
        var currentInterface = -1
        var currentAltSetting = -1
        var isAudioStreaming = false
        var currentTerminalLink = 0
        var currentIsPcmFormat = true
        var currentChannelCount = 0
        var currentSubslotSizeBytes = 0
        var currentBitResolution = 0
        var currentDiscreteSampleRatesHz = intArrayOf()
        var currentMinSampleRateHz = 0
        var currentMaxSampleRateHz = 0
        var lastEndpointIndex = -1

        var offset = 0
        while (offset < desc.size) {
            val length = desc[offset].toInt() and 0xFF
            if (length < 2 || offset + length > desc.size) break
            val type = desc[offset + 1].toInt() and 0xFF

            when (type) {
                DESC_TYPE_INTERFACE -> {
                    if (length >= 9) {
                        currentInterface = desc[offset + 2].toInt() and 0xFF
                        currentAltSetting = desc[offset + 3].toInt() and 0xFF
                        val ifaceClass = desc[offset + 5].toInt() and 0xFF
                        val ifaceSubclass = desc[offset + 6].toInt() and 0xFF
                        isAudioStreaming = (ifaceClass == CLASS_AUDIO &&
                            ifaceSubclass == SUBCLASS_AUDIO_STREAMING)
                        currentTerminalLink = 0
                        currentIsPcmFormat = true
                        currentChannelCount = 0
                        currentSubslotSizeBytes = 0
                        currentBitResolution = 0
                        currentDiscreteSampleRatesHz = intArrayOf()
                        currentMinSampleRateHz = 0
                        currentMaxSampleRateHz = 0
                        lastEndpointIndex = -1
                    }
                }
                DESC_TYPE_CS_INTERFACE -> {
                    if (isAudioStreaming && length >= 4) {
                        when (desc[offset + 2].toInt() and 0xff) {
                            0x01 -> {
                                currentTerminalLink = desc[offset + 3].toInt() and 0xff
                                if (topology.audioClassVersion >= 2 && length >= 11) {
                                    val formatType = desc[offset + 5].toInt() and 0xff
                                    val formatBitmap = readU32Le(desc, offset + 6)
                                    currentIsPcmFormat = formatType == 1 && (formatBitmap and 1L) != 0L
                                    currentChannelCount = desc[offset + 10].toInt() and 0xff
                                } else if (topology.audioClassVersion < 2 && length >= 7) {
                                    val formatTag = (desc[offset + 5].toInt() and 0xff) or
                                        ((desc[offset + 6].toInt() and 0xff) shl 8)
                                    currentIsPcmFormat = formatTag == 1
                                }
                            }
                            0x02 -> if (topology.audioClassVersion >= 2 && length >= 6) {
                                currentIsPcmFormat = currentIsPcmFormat &&
                                    (desc[offset + 3].toInt() and 0xff) == 1
                                currentSubslotSizeBytes = desc[offset + 4].toInt() and 0xff
                                currentBitResolution = desc[offset + 5].toInt() and 0xff
                            } else if (length >= 8) {
                                currentIsPcmFormat = currentIsPcmFormat &&
                                    (desc[offset + 3].toInt() and 0xff) == 1
                                currentChannelCount = desc[offset + 4].toInt() and 0xff
                                currentSubslotSizeBytes = desc[offset + 5].toInt() and 0xff
                                currentBitResolution = desc[offset + 6].toInt() and 0xff
                                val sampleFrequencyType = desc[offset + 7].toInt() and 0xff
                                if (sampleFrequencyType > 0 && length >= 8 + sampleFrequencyType * 3) {
                                    currentDiscreteSampleRatesHz = IntArray(sampleFrequencyType) { index ->
                                        readU24Le(desc, offset + 8 + index * 3)
                                    }
                                } else if (sampleFrequencyType == 0 && length >= 14) {
                                    currentMinSampleRateHz = readU24Le(desc, offset + 8)
                                    currentMaxSampleRateHz = readU24Le(desc, offset + 11)
                                }
                            }
                        }
                    }
                }
                DESC_TYPE_ENDPOINT -> {
                    if (length >= 7 && isAudioStreaming) {
                        val address = desc[offset + 2].toInt() and 0xFF
                        val attributes = desc[offset + 3].toInt() and 0xFF
                        val maxPacketSize = (desc[offset + 4].toInt() and 0xFF) or
                            ((desc[offset + 5].toInt() and 0xFF) shl 8)
                        val interval = desc[offset + 6].toInt() and 0xFF
                        val clockPath = topology.clockPath(currentTerminalLink)
                        results.add(
                            RawEndpointInfo(
                                address = address,
                                attributes = attributes,
                                maxPacketSize = maxPacketSize,
                                interval = interval,
                                interfaceNumber = currentInterface,
                                alternateSetting = currentAltSetting,
                                synchronizationType = (attributes shr 2) and 0x3,
                                transactionsPerServiceInterval = ((maxPacketSize shr 11) and 0x3) + 1,
                                audioClassVersion = topology.audioClassVersion,
                                controlInterfaceNumber = topology.controlInterfaceNumber,
                                clockSourceEntityId = clockPath.sourceEntityIds.firstOrNull() ?: 0,
                                clockSourceEntityIds = clockPath.sourceEntityIds,
                                clockSourceFrequencyControls = clockPath.sourceFrequencyControls,
                                clockSelectorEntityId = clockPath.selectorEntityId,
                                clockSelectorControl = clockPath.selectorControl,
                                isPcmFormat = currentIsPcmFormat,
                                channelCount = currentChannelCount,
                                subslotSizeBytes = currentSubslotSizeBytes,
                                bitResolution = currentBitResolution,
                                discreteSampleRatesHz = currentDiscreteSampleRatesHz,
                                minSampleRateHz = currentMinSampleRateHz,
                                maxSampleRateHz = currentMaxSampleRateHz
                            )
                        )
                        lastEndpointIndex = results.lastIndex
                    }
                }
                DESC_TYPE_CS_ENDPOINT -> {
                    if (isAudioStreaming && lastEndpointIndex >= 0 && length >= 4 &&
                        (desc[offset + 2].toInt() and 0xff) == 0x01 &&
                        topology.audioClassVersion < 2
                    ) {
                        val hasSamplingFrequencyControl =
                            (desc[offset + 3].toInt() and 0x01) != 0
                        results[lastEndpointIndex] = results[lastEndpointIndex].copy(
                            sampleFrequencyControl = if (hasSamplingFrequencyControl) 3 else 0
                        )
                    }
                }
            }
            offset += length
        }
        return results
    }

    private fun readU24Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16)

    private fun readU32Le(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)

    internal fun parseTopology(desc: ByteArray): AudioTopology {
        var currentInterface = -1
        var currentSubclass = -1
        var controlInterface = 0
        var audioClassVersion = 1
        val clockSources = mutableListOf<ClockSourceInfo>()
        val clockSelectors = mutableListOf<ClockSelectorInfo>()
        val terminalClockEntities = linkedMapOf<Int, Int>()
        var offset = 0
        while (offset + 2 <= desc.size) {
            val length = desc[offset].toInt() and 0xff
            if (length < 2 || offset + length > desc.size) break
            val type = desc[offset + 1].toInt() and 0xff
            if (type == DESC_TYPE_INTERFACE && length >= 9) {
                currentInterface = desc[offset + 2].toInt() and 0xff
                val interfaceClass = desc[offset + 5].toInt() and 0xff
                currentSubclass = desc[offset + 6].toInt() and 0xff
                val protocol = desc[offset + 7].toInt() and 0xff
                if (interfaceClass == CLASS_AUDIO && currentSubclass == 0x01) {
                    controlInterface = currentInterface
                    if (protocol == 0x20) audioClassVersion = 2
                }
                if (interfaceClass == CLASS_AUDIO && protocol == 0x20) audioClassVersion = 2
            } else if (type == DESC_TYPE_CS_INTERFACE && currentSubclass == 0x01 && length >= 4) {
                val subtype = desc[offset + 2].toInt() and 0xff
                when (subtype) {
                    0x01 -> if (length >= 5) {
                        val bcdAdc = (desc[offset + 3].toInt() and 0xff) or
                            ((desc[offset + 4].toInt() and 0xff) shl 8)
                        if (bcdAdc >= 0x0200) audioClassVersion = 2
                    }
                    0x02 -> if (audioClassVersion >= 2 && length >= 8) {
                        val terminalId = desc[offset + 3].toInt() and 0xff
                        terminalClockEntities[terminalId] = desc[offset + 7].toInt() and 0xff
                    }
                    0x03 -> if (audioClassVersion >= 2 && length >= 9) {
                        val terminalId = desc[offset + 3].toInt() and 0xff
                        terminalClockEntities[terminalId] = desc[offset + 8].toInt() and 0xff
                    }
                    0x0A -> if (audioClassVersion >= 2 && length >= 6) {
                        clockSources += ClockSourceInfo(
                            entityId = desc[offset + 3].toInt() and 0xff,
                            frequencyControl = desc[offset + 5].toInt() and 0x03
                        )
                    }
                    0x0B -> if (audioClassVersion >= 2 && length >= 7) {
                        val pinCount = desc[offset + 4].toInt() and 0xff
                        if (pinCount > 0 && length >= 7 + pinCount) {
                            val sourceIds = IntArray(pinCount) { pin ->
                                desc[offset + 5 + pin].toInt() and 0xff
                            }
                            clockSelectors += ClockSelectorInfo(
                                entityId = desc[offset + 3].toInt() and 0xff,
                                sourceEntityIds = sourceIds,
                                selectorControl = desc[offset + 5 + pinCount].toInt() and 0x03
                            )
                        }
                    }
                }
            }
            offset += length
        }
        return AudioTopology(
            audioClassVersion = audioClassVersion,
            controlInterfaceNumber = controlInterface,
            clockSourceEntityId = clockSources.singleOrNull()?.entityId
                ?: clockSources.firstOrNull()?.entityId
                ?: 0,
            clockSources = clockSources,
            clockSelectors = clockSelectors,
            terminalClockEntities = terminalClockEntities
        )
    }
}
