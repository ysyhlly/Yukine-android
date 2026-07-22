package app.yukine.playback.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.IOException

internal interface UsbPcmTransport {
    fun write(pcmData: ByteArray): Int
    fun reset()
    fun cancel()
    fun close()
    fun metrics(): UsbTransferMetrics = UsbTransferMetrics()
}

internal data class UsbTransferMetrics(
    val submittedPackets: Long = 0,
    val completedPackets: Long = 0,
    val failedPackets: Long = 0,
    val feedbackRateHz: Double = 0.0,
    val lastError: String = ""
)

internal enum class UsbPcmTransportKind { BULK, ISOCHRONOUS, UNSUPPORTED }

internal fun usbPcmTransportKind(endpointType: Int): UsbPcmTransportKind = when (endpointType) {
    UsbConstants.USB_ENDPOINT_XFER_BULK -> UsbPcmTransportKind.BULK
    UsbConstants.USB_ENDPOINT_XFER_ISOC -> UsbPcmTransportKind.ISOCHRONOUS
    else -> UsbPcmTransportKind.UNSUPPORTED
}

internal fun interface UsbPcmTransportFactory {
    fun create(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        config: UsbAudioStreamConfig
    ): UsbPcmTransport
}

internal object DefaultUsbPcmTransportFactory : UsbPcmTransportFactory {
    override fun create(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        config: UsbAudioStreamConfig
    ): UsbPcmTransport = when (usbPcmTransportKind(endpoint.type)) {
        UsbPcmTransportKind.BULK -> BulkUsbPcmTransport(connection, endpoint, config.maxPacketSize)
        UsbPcmTransportKind.ISOCHRONOUS -> IsochronousUsbPcmTransport(connection, endpoint, config)
        UsbPcmTransportKind.UNSUPPORTED -> throw IOException(
            "Unsupported USB audio endpoint type: ${endpoint.type}"
        )
    }
}

private class BulkUsbPcmTransport(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val maxPacketSize: Int
) : UsbPcmTransport {
    private var submittedPackets = 0L
    private var completedPackets = 0L
    private var failedPackets = 0L
    private var error = ""

    override fun write(pcmData: ByteArray): Int {
        var offset = 0
        while (offset < pcmData.size) {
            val size = minOf(maxPacketSize, pcmData.size - offset)
            val packet = pcmData.copyOfRange(offset, offset + size)
            submittedPackets++
            val written = connection.bulkTransfer(endpoint, packet, size, BULK_TIMEOUT_MS)
            if (written <= 0) {
                failedPackets++
                error = "bulkTransfer returned $written"
                return -1
            }
            completedPackets++
            offset += written
        }
        return offset
    }

    override fun reset() = Unit
    override fun cancel() = Unit
    override fun close() = Unit

    override fun metrics(): UsbTransferMetrics = UsbTransferMetrics(
        submittedPackets = submittedPackets,
        completedPackets = completedPackets,
        failedPackets = failedPackets,
        lastError = error
    )

    private companion object {
        const val BULK_TIMEOUT_MS = 200
    }
}

private class IsochronousUsbPcmTransport(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    config: UsbAudioStreamConfig
) : UsbPcmTransport {
    init {
        if (!NativeUsbIsoBridge.available) {
            throw IOException("USB isochronous native library unavailable: ${NativeUsbIsoBridge.loadError}")
        }
    }

    private var nativeHandle = NativeUsbIsoBridge.open(
        connection.fileDescriptor,
        endpoint.address,
        endpoint.maxPacketSize,
        endpoint.interval,
        config.sampleRateHz,
        config.bytesPerFrame,
        config.interfaceNumber,
        config.alternateSetting,
        config.feedbackEndpointAddress,
        config.feedbackMaxPacketSize,
        config.audioClassVersion,
        config.controlInterfaceNumber,
        config.clockSourceEntityIds,
        config.clockSourceFrequencyControls,
        config.clockSelectorEntityId,
        config.clockSelectorControl,
        config.sampleFrequencyControl
    )

    init {
        if (nativeHandle == 0L) {
            throw IOException("Could not open USB isochronous transport: ${NativeUsbIsoBridge.lastError()}")
        }
    }

    override fun write(pcmData: ByteArray): Int {
        val handle = nativeHandle
        return if (handle != 0L) NativeUsbIsoBridge.write(handle, pcmData) else -1
    }

    override fun reset() {
        nativeHandle.takeIf { it != 0L }?.let(NativeUsbIsoBridge::reset)
    }

    override fun cancel() {
        nativeHandle.takeIf { it != 0L }?.let(NativeUsbIsoBridge::cancel)
    }

    override fun close() {
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) NativeUsbIsoBridge.close(handle)
    }

    override fun metrics(): UsbTransferMetrics {
        val handle = nativeHandle
        if (handle == 0L) return UsbTransferMetrics(lastError = NativeUsbIsoBridge.lastError())
        val values = NativeUsbIsoBridge.metrics(handle)
        return UsbTransferMetrics(
            submittedPackets = values.getOrElse(0) { 0L },
            completedPackets = values.getOrElse(1) { 0L },
            failedPackets = values.getOrElse(2) { 0L },
            feedbackRateHz = java.lang.Double.longBitsToDouble(values.getOrElse(3) { 0L }),
            lastError = NativeUsbIsoBridge.lastError()
        )
    }
}

private object NativeUsbIsoBridge {
    val loadError: String
    val available: Boolean

    init {
        var error = ""
        available = try {
            System.loadLibrary("echo_usb_iso")
            true
        } catch (failure: LinkageError) {
            error = failure.message.orEmpty()
            false
        }
        loadError = error
    }

    external fun open(
        fileDescriptor: Int,
        endpointAddress: Int,
        maxPacketSize: Int,
        interval: Int,
        sampleRateHz: Int,
        bytesPerFrame: Int,
        interfaceNumber: Int,
        alternateSetting: Int,
        feedbackEndpointAddress: Int,
        feedbackMaxPacketSize: Int,
        audioClassVersion: Int,
        controlInterfaceNumber: Int,
        clockSourceEntityIds: IntArray,
        clockSourceFrequencyControls: IntArray,
        clockSelectorEntityId: Int,
        clockSelectorControl: Int,
        sampleFrequencyControl: Int
    ): Long

    external fun write(handle: Long, pcmData: ByteArray): Int
    external fun reset(handle: Long)
    external fun cancel(handle: Long)
    external fun close(handle: Long)
    external fun metrics(handle: Long): LongArray
    external fun lastError(): String
}
