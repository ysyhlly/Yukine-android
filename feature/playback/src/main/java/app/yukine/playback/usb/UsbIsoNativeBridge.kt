package app.yukine.playback.usb

import java.io.IOException

internal data class UsbIsoOpenRequest(
    val fileDescriptor: Int,
    val endpointAddress: Int,
    val maxPacketSize: Int,
    val interval: Int,
    val sampleRateHz: Int,
    val bytesPerFrame: Int,
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val feedbackEndpointAddress: Int,
    val feedbackMaxPacketSize: Int,
    val audioClassVersion: Int,
    val controlInterfaceNumber: Int,
    val clockSourceEntityIds: IntArray,
    val clockSourceFrequencyControls: IntArray,
    val clockSelectorEntityId: Int,
    val clockSelectorControl: Int,
    val sampleFrequencyControl: Int,
    val allowUac2PcmRateMismatch: Boolean
)

internal interface UsbIsoNativeBridge {
    val available: Boolean
    val loadError: String

    fun open(request: UsbIsoOpenRequest): Long
    fun write(handle: Long, pcmData: ByteArray): Int
    fun reset(handle: Long)
    fun cancel(handle: Long)
    fun close(handle: Long)
    fun metrics(handle: Long): LongArray
    fun lastError(): String
}

internal data class OpenedUsbIsoTransport(
    val bridge: UsbIsoNativeBridge,
    val handle: Long
)

internal fun openRustUsbIsoTransport(
    request: UsbIsoOpenRequest,
    bridge: UsbIsoNativeBridge = RustUsbIsoNativeBridge
): OpenedUsbIsoTransport {
    if (!bridge.available) {
        throw IOException("Rust USB isochronous backend unavailable: ${bridge.loadError}")
    }
    val handle = try {
        bridge.open(request)
    } catch (failure: LinkageError) {
        throw IOException(
            "Rust USB isochronous backend unavailable: " +
                failure.message.orEmpty().ifBlank { failure.javaClass.simpleName },
            failure
        )
    }
    if (handle == 0L) {
        throw IOException("Rust USB isochronous backend unavailable: ${bridgeError(bridge)}")
    }
    return OpenedUsbIsoTransport(bridge, handle)
}

private fun bridgeError(bridge: UsbIsoNativeBridge): String = try {
    bridge.lastError().ifBlank { "native open failed" }
} catch (failure: LinkageError) {
    failure.message.orEmpty().ifBlank { failure.javaClass.simpleName }
}

private object RustUsbIsoNativeBridge : UsbIsoNativeBridge {
    override val available: Boolean get() = RustUsbIsoBridge.available
    override val loadError: String get() = RustUsbIsoBridge.loadError

    override fun open(request: UsbIsoOpenRequest): Long = RustUsbIsoBridge.open(
        request.fileDescriptor,
        request.endpointAddress,
        request.maxPacketSize,
        request.interval,
        request.sampleRateHz,
        request.bytesPerFrame,
        request.interfaceNumber,
        request.alternateSetting,
        request.feedbackEndpointAddress,
        request.feedbackMaxPacketSize,
        request.audioClassVersion,
        request.controlInterfaceNumber,
        request.clockSourceEntityIds,
        request.clockSourceFrequencyControls,
        request.clockSelectorEntityId,
        request.clockSelectorControl,
        request.sampleFrequencyControl,
        request.allowUac2PcmRateMismatch
    )

    override fun write(handle: Long, pcmData: ByteArray): Int =
        RustUsbIsoBridge.write(handle, pcmData)
    override fun reset(handle: Long) = RustUsbIsoBridge.reset(handle)
    override fun cancel(handle: Long) = RustUsbIsoBridge.cancel(handle)
    override fun close(handle: Long) = RustUsbIsoBridge.close(handle)
    override fun metrics(handle: Long): LongArray = RustUsbIsoBridge.metrics(handle)
    override fun lastError(): String = RustUsbIsoBridge.lastError()
}

private object RustUsbIsoBridge {
    val loadError: String
    val available: Boolean

    init {
        var error = ""
        available = try {
            System.loadLibrary("echo_usb_iso_rust")
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
        sampleFrequencyControl: Int,
        allowUac2PcmRateMismatch: Boolean
    ): Long

    external fun write(handle: Long, pcmData: ByteArray): Int
    external fun reset(handle: Long)
    external fun cancel(handle: Long)
    external fun close(handle: Long)
    external fun metrics(handle: Long): LongArray
    external fun lastError(): String
}
