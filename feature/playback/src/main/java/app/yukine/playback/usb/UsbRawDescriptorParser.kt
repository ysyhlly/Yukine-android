package app.yukine.playback.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import java.lang.reflect.Constructor

/**
 * Parses raw USB configuration descriptors to find audio streaming endpoints
 * on alternate setting 1, which Android's standard UsbInterface API does not expose.
 *
 * Uses reflection to construct [UsbEndpoint] objects from parsed descriptor data,
 * enabling direct USB audio output without native code (libusb/NDK).
 */
internal object UsbRawDescriptorParser {

    private const val TAG = "UsbRawDescriptorParser"

    // USB descriptor types
    private const val DESC_TYPE_CONFIGURATION = 0x02
    private const val DESC_TYPE_INTERFACE = 0x04
    private const val DESC_TYPE_ENDPOINT = 0x05

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
        val alternateSetting: Int
    )

    /**
     * Reads the raw configuration descriptor and finds the audio streaming OUT endpoint
     * on alternate setting 1.
     *
     * @param conn Open USB device connection.
     * @return RawEndpointInfo or null if not found.
     */
    fun findAudioStreamingEndpoint(conn: UsbDeviceConnection): RawEndpointInfo? {
        val rawDesc = readConfigurationDescriptor(conn) ?: return null
        val endpoints = parseEndpoints(rawDesc)

        // Find OUT endpoint on Audio Streaming interface, preferring alt setting 1
        val outEndpoint = endpoints.firstOrNull { ep ->
            val isOut = (ep.address and 0x80) == 0
            val isIsoOrBulk = (ep.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                (ep.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_BULK
            isOut && isIsoOrBulk && ep.alternateSetting > 0
        } ?: endpoints.firstOrNull { ep ->
            val isOut = (ep.address and 0x80) == 0
            val isIsoOrBulk = (ep.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                (ep.attributes and 0x03) == UsbConstants.USB_ENDPOINT_XFER_BULK
            isOut && isIsoOrBulk
        }

        if (outEndpoint != null) {
            Log.d(TAG, "Found audio endpoint: addr=0x${Integer.toHexString(outEndpoint.address)}, " +
                "maxPkt=${outEndpoint.maxPacketSize}, alt=${outEndpoint.alternateSetting}, " +
                "iface=#${outEndpoint.interfaceNumber}")
        }
        return outEndpoint
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
            Log.d(TAG, "Created UsbEndpoint via reflection: 0x${Integer.toHexString(raw.address)}")
            endpoint
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create UsbEndpoint via reflection: ${e.message}")
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
            Log.w(TAG, "Failed to read config descriptor header: $headerRead bytes")
            return null
        }

        // wTotalLength is at offset 2-3 (little-endian)
        val totalLength = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        if (totalLength < 9 || totalLength > 4096) {
            Log.w(TAG, "Invalid config descriptor length: $totalLength")
            return null
        }

        // Read the full configuration descriptor
        val fullDesc = ByteArray(totalLength)
        val fullRead = conn.controlTransfer(
            0x80, 0x06, 0x0200, 0,
            fullDesc, totalLength, 1000
        )
        if (fullRead < totalLength) {
            Log.w(TAG, "Incomplete config descriptor: got $fullRead/$totalLength bytes")
            return null
        }

        return fullDesc
    }

    /**
     * Parses raw configuration descriptor bytes to extract endpoint information
     * from Audio Streaming interfaces.
     */
    private fun parseEndpoints(desc: ByteArray): List<RawEndpointInfo> {
        val results = mutableListOf<RawEndpointInfo>()
        var currentInterface = -1
        var currentAltSetting = -1
        var isAudioStreaming = false

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
                    }
                }
                DESC_TYPE_ENDPOINT -> {
                    if (length >= 7 && isAudioStreaming) {
                        val address = desc[offset + 2].toInt() and 0xFF
                        val attributes = desc[offset + 3].toInt() and 0xFF
                        val maxPacketSize = (desc[offset + 4].toInt() and 0xFF) or
                            ((desc[offset + 5].toInt() and 0xFF) shl 8)
                        val interval = desc[offset + 6].toInt() and 0xFF
                        results.add(
                            RawEndpointInfo(
                                address = address,
                                attributes = attributes,
                                maxPacketSize = maxPacketSize,
                                interval = interval,
                                interfaceNumber = currentInterface,
                                alternateSetting = currentAltSetting
                            )
                        )
                    }
                }
            }
            offset += length
        }
        return results
    }
}
