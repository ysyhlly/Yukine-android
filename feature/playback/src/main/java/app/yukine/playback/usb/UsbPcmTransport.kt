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

/**
 * Keeps the native pointer alive for the full duration of every JNI call.
 *
 * A volatile handle alone is insufficient: close could still free the native state after another
 * thread reads a non-zero handle but before that thread enters JNI.
 */
internal class SynchronizedNativeHandle(initialHandle: Long) {
    private val lock = Any()
    private var handle = initialHandle

    fun isOpen(): Boolean = synchronized(lock) {
        handle != 0L
    }

    fun <T> withHandle(closedValue: T, block: (Long) -> T): T = synchronized(lock) {
        val currentHandle = handle
        if (currentHandle == 0L) closedValue else block(currentHandle)
    }

    fun close(block: (Long) -> Unit) = synchronized(lock) {
        val currentHandle = handle
        if (currentHandle != 0L) {
            handle = 0L
            block(currentHandle)
        }
    }
}

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
        return writeUsbPackets(pcmData, maxPacketSize) { buffer, offset, size ->
            submittedPackets++
            val written = connection.bulkTransfer(
                endpoint,
                buffer,
                offset,
                size,
                BULK_TIMEOUT_MS
            )
            if (written <= 0) {
                failedPackets++
                error = "bulkTransfer returned $written"
            } else {
                completedPackets++
            }
            written
        }
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

internal fun writeUsbPackets(
    pcmData: ByteArray,
    maxPacketSize: Int,
    transfer: (buffer: ByteArray, offset: Int, size: Int) -> Int
): Int {
    require(maxPacketSize > 0)
    var offset = 0
    while (offset < pcmData.size) {
        val size = minOf(maxPacketSize, pcmData.size - offset)
        val written = transfer(pcmData, offset, size)
        if (written <= 0) return -1
        offset += written
    }
    return offset
}

private class IsochronousUsbPcmTransport(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    config: UsbAudioStreamConfig
) : UsbPcmTransport {
    private val opened = openRustUsbIsoTransport(
        UsbIsoOpenRequest(
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
            config.sampleFrequencyControl,
            config.allowUac2PcmRateMismatch
        )
    )
    private val nativeBridge = opened.bridge
    private val nativeHandle = SynchronizedNativeHandle(opened.handle)

    override fun write(pcmData: ByteArray): Int =
        nativeHandle.withHandle(-1) { handle ->
            nativeBridge.write(handle, pcmData)
        }

    override fun reset() {
        nativeHandle.withHandle(Unit) { handle ->
            nativeBridge.reset(handle)
        }
    }

    override fun cancel() {
        nativeHandle.withHandle(Unit) { handle ->
            nativeBridge.cancel(handle)
        }
    }

    override fun close() {
        nativeHandle.close(nativeBridge::close)
    }

    override fun metrics(): UsbTransferMetrics {
        val values = nativeHandle.withHandle<LongArray?>(null) { handle ->
            nativeBridge.metrics(handle)
        } ?: return UsbTransferMetrics(lastError = nativeBridge.lastError())
        return UsbTransferMetrics(
            submittedPackets = values.getOrElse(0) { 0L },
            completedPackets = values.getOrElse(1) { 0L },
            failedPackets = values.getOrElse(2) { 0L },
            feedbackRateHz = java.lang.Double.longBitsToDouble(values.getOrElse(3) { 0L }),
            lastError = nativeBridge.lastError()
        )
    }
}
