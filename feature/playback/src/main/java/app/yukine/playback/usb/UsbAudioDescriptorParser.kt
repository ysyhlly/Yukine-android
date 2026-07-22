package app.yukine.playback.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import app.yukine.diagnostics.DiagnosticLog

/**
 * Parses USB Audio Class descriptors to find a suitable Audio Streaming endpoint
 * for bit-perfect PCM output.
 *
 * Supports both UAC1 (USB Audio Class 1.0) and UAC2 (USB Audio Class 2.0) devices.
 * The parser looks for an Audio Streaming interface (class=0x01, subclass=0x02) with
 * a bulk or isochronous OUT endpoint.
 */
internal object UsbAudioDescriptorParser {

    private const val TAG = "UsbAudioDescriptorParser"

    // USB Audio Class subclass codes
    private const val USB_SUBCLASS_AUDIO_STREAMING = 0x02

    private const val CS_SAMPLE_FREQ_CONTROL = 0x01

    // USB Audio Class 2.0 request codes
    private const val UAC2_SET_CUR = 0x01

    /**
     * Finds the best audio streaming configuration for the given USB device.
     *
     * @param device The USB device to inspect.
     * @param preferredSampleRateHz Preferred sample rate (e.g., source file's rate).
     * @param preferredBitDepth Preferred bit depth (16, 24, or 32).
     * @return A [UsbAudioStreamConfig] or null if no suitable configuration found.
     */
    fun findAudioStreamConfig(
        device: UsbDevice,
        preferredSampleRateHz: Int = 48000,
        preferredBitDepth: Int = 16
    ): UsbAudioStreamConfig? {
        var streamingInterface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            if (usbInterface.interfaceSubclass != USB_SUBCLASS_AUDIO_STREAMING) continue

            val config = parseAudioStreamingInterface(
                usbInterface,
                preferredSampleRateHz,
                preferredBitDepth
            )
            if (config != null) {
                DiagnosticLog.d(TAG, "Found audio stream config: $config")
                return config
            }
            // Remember the streaming interface even if no endpoints on alt 0.
            // Endpoints are likely on alternate setting 1.
            if (streamingInterface == null) {
                streamingInterface = usbInterface
            }
        }

        // No endpoints found on alt 0 — return a config based on the streaming interface
        // with estimated parameters. The endpoint will be discovered after selecting alt 1.
        if (streamingInterface != null) {
            DiagnosticLog.d(TAG, "Audio streaming interface #${streamingInterface.id} found but no " +
                "endpoints on alt 0 (endpoints likely on alt 1). Using estimated config.")
            return UsbAudioStreamConfig(
                endpointAddress = 0, // Will be discovered after alt setting selection
                maxPacketSize = 192, // 48kHz * 2ch * 16bit / 1000
                sampleRateHz = preferredSampleRateHz,
                bitDepth = preferredBitDepth.coerceIn(16, 32),
                channelCount = 2,
                interfaceNumber = streamingInterface.id,
                alternateSetting = 1 // Endpoints are on alt 1
            )
        }

        DiagnosticLog.w(TAG, "No suitable audio streaming interface found on device: ${device.productName}")
        return null
    }

    private fun parseAudioStreamingInterface(
        usbInterface: UsbInterface,
        preferredSampleRateHz: Int,
        preferredBitDepth: Int
    ): UsbAudioStreamConfig? {
        // Find an OUT endpoint (bulk or isochronous)
        var outEndpoint: UsbEndpoint? = null
        var feedbackEndpoint: UsbEndpoint? = null

        for (j in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(j)
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                val type = endpoint.type
                if (type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                    type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                ) {
                    outEndpoint = endpoint
                }
            } else if (endpoint.direction == UsbConstants.USB_DIR_IN &&
                endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
            ) {
                // Feedback endpoint (optional, for clock synchronization)
                feedbackEndpoint = endpoint
            }
        }

        val ep = outEndpoint ?: return null

        // Determine bit depth and sample rate
        // Since Android's UsbInterface doesn't expose raw descriptors directly,
        // we use sensible defaults based on common DAC capabilities.
        val bitDepth = preferredBitDepth.coerceIn(16, 32)
        val sampleRate = preferredSampleRateHz
        val channelCount = 2 // Assume stereo; most DACs are stereo

        return UsbAudioStreamConfig(
            endpointAddress = ep.address,
            maxPacketSize = ep.maxPacketSize,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channelCount = channelCount,
            interfaceNumber = usbInterface.id,
            alternateSetting = usbInterface.alternateSetting,
            feedbackEndpointAddress = feedbackEndpoint?.address ?: 0,
            endpointType = ep.type,
            interval = ep.interval
        )
    }

    /**
     * Sets the sample rate on a UAC2 device via control transfer.
     *
     * @param connection Open USB device connection.
     * @param config The audio stream configuration.
     * @param sampleRateHz Target sample rate.
     * @return true if the sample rate was set successfully.
     */
    fun setSampleRate(
        connection: UsbDeviceConnection,
        config: UsbAudioStreamConfig,
        sampleRateHz: Int
    ): Boolean {
        return try {
            val uac2 = config.audioClassVersion >= 2
            if (uac2 && config.clockSourceEntityId <= 0) {
                DiagnosticLog.w(TAG, "UAC2 clock source was not resolved; refusing guessed entity control")
                return false
            }
            val data = if (uac2) {
                byteArrayOf(
                    (sampleRateHz and 0xFF).toByte(),
                    ((sampleRateHz shr 8) and 0xFF).toByte(),
                    ((sampleRateHz shr 16) and 0xFF).toByte(),
                    ((sampleRateHz shr 24) and 0xFF).toByte()
                )
            } else {
                byteArrayOf(
                    (sampleRateHz and 0xFF).toByte(),
                    ((sampleRateHz shr 8) and 0xFF).toByte(),
                    ((sampleRateHz shr 16) and 0xFF).toByte()
                )
            }
            val result = connection.controlTransfer(
                if (uac2) 0x21 else 0x22,
                UAC2_SET_CUR, // bRequest
                (CS_SAMPLE_FREQ_CONTROL shl 8),
                if (uac2) {
                    (config.clockSourceEntityId shl 8) or (config.interfaceNumber and 0xff)
                } else {
                    config.endpointAddress and 0xff
                },
                data,
                data.size,
                1000 // timeout ms
            )
            result >= 0
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "Failed to set sample rate: ${e.message}")
            false
        }
    }
}
