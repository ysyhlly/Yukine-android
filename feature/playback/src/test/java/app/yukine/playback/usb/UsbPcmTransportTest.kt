package app.yukine.playback.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class UsbPcmTransportTest {
    @Test
    fun packetWriterUsesOffsetsWithoutCopyingInputBuffer() {
        val input = ByteArray(10)
        val calls = mutableListOf<Pair<Int, Int>>()

        val written = writeUsbPackets(input, maxPacketSize = 4) { buffer, offset, size ->
            assertSame(input, buffer)
            calls += offset to size
            size
        }

        assertEquals(10, written)
        assertEquals(listOf(0 to 4, 4 to 4, 8 to 2), calls)
    }

    @Test
    fun packetWriterStopsAfterTransportFailure() {
        val calls = mutableListOf<Int>()

        val written = writeUsbPackets(ByteArray(10), maxPacketSize = 4) { _, offset, size ->
            calls += offset
            if (offset == 4) -1 else size
        }

        assertEquals(-1, written)
        assertEquals(listOf(0, 4), calls)
    }

    @Test
    fun nativeHandleCloseWaitsForInFlightCallAndIsIdempotent() {
        val guard = SynchronizedNativeHandle(73L)
        val callEntered = CountDownLatch(1)
        val releaseCall = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)
        val observedHandle = AtomicLong()
        val closedHandle = AtomicLong()
        val closeCount = AtomicInteger()

        val caller = thread(name = "usb-native-caller") {
            guard.withHandle(Unit) { handle ->
                observedHandle.set(handle)
                callEntered.countDown()
                releaseCall.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(callEntered.await(2, TimeUnit.SECONDS))

        val closer = thread(name = "usb-native-closer") {
            closeStarted.countDown()
            guard.close { handle ->
                closedHandle.set(handle)
                closeCount.incrementAndGet()
            }
            closeFinished.countDown()
        }
        assertTrue(closeStarted.await(2, TimeUnit.SECONDS))
        assertFalse(closeFinished.await(100, TimeUnit.MILLISECONDS))

        releaseCall.countDown()
        caller.join(2_000L)
        closer.join(2_000L)

        assertFalse(caller.isAlive)
        assertFalse(closer.isAlive)
        assertEquals(73L, observedHandle.get())
        assertEquals(73L, closedHandle.get())
        assertEquals(-1L, guard.withHandle(-1L) { it })

        guard.close { closeCount.incrementAndGet() }
        assertEquals(1, closeCount.get())
    }
}
