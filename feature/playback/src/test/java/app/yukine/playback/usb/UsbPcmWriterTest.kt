package app.yukine.playback.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class UsbPcmWriterTest {
    @Test
    fun shortWriteReportsErrorAndStopStillClosesTransport() {
        val transport = FakeTransport(shortWrite = true)
        val error = CountDownLatch(1)
        val writer = UsbPcmWriter(UsbAudioStreamConfig.FALLBACK, transport, { error.countDown() })

        writer.start()
        assertTrue(writer.queueBuffer(ByteArray(192)))
        assertTrue(error.await(2, TimeUnit.SECONDS))
        writer.stop()

        assertTrue(transport.cancelled)
        assertTrue(transport.closed)
    }

    @Test
    fun successfulWriteAdvancesFramesAndPublishesMetrics() {
        val metrics = mutableListOf<UsbPcmWriterMetrics>()
        val writer = UsbPcmWriter(
            UsbAudioStreamConfig.FALLBACK,
            FakeTransport(shortWrite = false),
            { throw AssertionError(it) },
            metrics::add
        )

        writer.start()
        assertTrue(writer.queueBuffer(ByteArray(192)))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (writer.totalFramesWritten < 48 && System.nanoTime() < deadline) Thread.yield()
        writer.stop()

        assertEquals(48L, writer.totalFramesWritten)
        assertTrue(metrics.any { it.framesWritten == 48L })
    }

    private class FakeTransport(private val shortWrite: Boolean) : UsbPcmTransport {
        var cancelled = false
        var closed = false
        private var submitted = 0L

        override fun write(pcmData: ByteArray): Int {
            submitted++
            return if (shortWrite) pcmData.size - 1 else pcmData.size
        }
        override fun reset() = Unit
        override fun cancel() { cancelled = true }
        override fun close() { closed = true }
        override fun metrics(): UsbTransferMetrics = UsbTransferMetrics(
            submittedPackets = submitted,
            completedPackets = if (shortWrite) 0 else submitted,
            failedPackets = if (shortWrite) submitted else 0,
            lastError = if (shortWrite) "short write" else ""
        )
    }
}
