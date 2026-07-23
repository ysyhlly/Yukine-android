package app.yukine.playback.usb

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbIsoNativeBridgeTest {
    @Test
    fun opensRustBackendWithoutSelectorOrFallback() {
        val rust = FakeBridge(openHandle = 22L)

        val opened = openRustUsbIsoTransport(request(), rust)

        assertSame(rust, opened.bridge)
        assertEquals(22L, opened.handle)
        assertEquals(1, rust.openCalls)
    }

    @Test
    fun unavailableRustLibraryFailsDirectly() {
        val rust = FakeBridge(available = false, loadError = "missing Rust library")

        val failure = assertThrows(IOException::class.java) {
            openRustUsbIsoTransport(request(), rust)
        }

        assertTrue(failure.message.orEmpty().contains("missing Rust library"))
        assertEquals(0, rust.openCalls)
    }

    @Test
    fun rustOpenFailureReportsNativeError() {
        val rust = FakeBridge(openHandle = 0L, error = "rust open failed")

        val failure = assertThrows(IOException::class.java) {
            openRustUsbIsoTransport(request(), rust)
        }

        assertTrue(failure.message.orEmpty().contains("rust open failed"))
        assertEquals(1, rust.openCalls)
    }

    @Test
    fun rustJniLinkFailureFailsDirectly() {
        val rust = FakeBridge(openFailure = UnsatisfiedLinkError("missing Rust JNI symbol"))

        val failure = assertThrows(IOException::class.java) {
            openRustUsbIsoTransport(request(), rust)
        }

        assertTrue(failure.message.orEmpty().contains("missing Rust JNI symbol"))
        assertEquals(1, rust.openCalls)
    }

    @Test
    fun runtimeWriteFailureStaysOnRustBackend() {
        val rust = FakeBridge(openHandle = 22L, writeResult = -1)
        val opened = openRustUsbIsoTransport(request(), rust)

        val writeResult = opened.bridge.write(opened.handle, byteArrayOf(1, 2))

        assertSame(rust, opened.bridge)
        assertEquals(-1, writeResult)
        assertEquals(1, rust.writeCalls)
    }

    private fun request() = UsbIsoOpenRequest(
        fileDescriptor = 1,
        endpointAddress = 1,
        maxPacketSize = 192,
        interval = 1,
        sampleRateHz = 48_000,
        bytesPerFrame = 4,
        interfaceNumber = 1,
        alternateSetting = 1,
        feedbackEndpointAddress = 0,
        feedbackMaxPacketSize = 0,
        audioClassVersion = 2,
        controlInterfaceNumber = 0,
        clockSourceEntityIds = intArrayOf(10),
        clockSourceFrequencyControls = intArrayOf(3),
        clockSelectorEntityId = 0,
        clockSelectorControl = -1,
        sampleFrequencyControl = -1,
        allowUac2PcmRateMismatch = false
    )

    private class FakeBridge(
        override val available: Boolean = true,
        override val loadError: String = "",
        private val openHandle: Long = 0L,
        private val error: String = "",
        private val writeResult: Int = 0,
        private val openFailure: LinkageError? = null
    ) : UsbIsoNativeBridge {
        var openCalls = 0
        var writeCalls = 0

        override fun open(request: UsbIsoOpenRequest): Long {
            openCalls++
            openFailure?.let { throw it }
            return openHandle
        }

        override fun write(handle: Long, pcmData: ByteArray): Int {
            writeCalls++
            return writeResult
        }

        override fun reset(handle: Long) = Unit
        override fun cancel(handle: Long) = Unit
        override fun close(handle: Long) = Unit
        override fun metrics(handle: Long): LongArray = longArrayOf()
        override fun lastError(): String = error
    }
}
