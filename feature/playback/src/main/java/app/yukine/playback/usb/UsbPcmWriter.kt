package app.yukine.playback.usb

import android.hardware.usb.UsbDeviceConnection
import android.os.HandlerThread
import android.os.Handler
import android.os.Process
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated writer thread that transfers PCM audio data to a USB DAC via bulk transfer.
 *
 * @param config USB audio stream configuration.
 * @param endpoint The USB endpoint to write to.
 * @param onError Callback invoked when a fatal USB write error occurs.
 */
internal class UsbPcmWriter(
    private val config: UsbAudioStreamConfig,
    private val endpoint: android.hardware.usb.UsbEndpoint,
    private val onError: () -> Unit
) {
    companion object {
        private const val TAG = "UsbPcmWriter"
        private const val QUEUE_CAPACITY = 64
        private const val BULK_TIMEOUT_MS = 200
    }

    private val bufferQueue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private val running = AtomicBoolean(false)
    private val framesWritten = AtomicLong(0)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var connection: UsbDeviceConnection? = null

    /** Total audio frames successfully written to the USB endpoint. */
    val totalFramesWritten: Long get() = framesWritten.get()

    /** Current playback position in microseconds based on frames written. */
    val currentPositionUs: Long
        get() {
            val rate = config.sampleRateHz
            return if (rate > 0) framesWritten.get() * 1_000_000L / rate else 0L
        }

    /**
     * Starts the writer thread and begins consuming buffers.
     *
     * @param usbConnection Open USB device connection.
     */
    fun start(usbConnection: UsbDeviceConnection) {
        if (running.getAndSet(true)) return
        connection = usbConnection
        framesWritten.set(0)
        bufferQueue.clear()

        val ht = HandlerThread("UsbPcmWriter", Process.THREAD_PRIORITY_AUDIO)
        ht.start()
        thread = ht
        handler = Handler(ht.looper)
        handler?.post(::writeLoop)
        Log.d(TAG, "USB PCM writer started, endpoint=0x${Integer.toHexString(config.endpointAddress)}")
    }

    /**
     * Stops the writer thread and releases resources.
     */
    fun stop() {
        if (!running.getAndSet(false)) return
        bufferQueue.clear()
        thread?.quitSafely()
        thread = null
        handler = null
        connection = null
        Log.d(TAG, "USB PCM writer stopped, totalFrames=${framesWritten.get()}")
    }

    /**
     * Queues a PCM buffer for writing to the USB DAC.
     *
     * @param pcmData Raw PCM byte array.
     * @return true if the buffer was queued, false if the queue is full or writer is stopped.
     */
    fun queueBuffer(pcmData: ByteArray): Boolean {
        if (!running.get()) return false
        return bufferQueue.offer(pcmData)
    }

    /**
     * Resets the frame counter (called on flush/seek).
     */
    fun resetPosition() {
        framesWritten.set(0)
        bufferQueue.clear()
    }

    /**
     * Returns the number of buffers currently queued.
     */
    fun queuedBufferCount(): Int = bufferQueue.size

    private fun writeLoop() {
        val conn = connection ?: return
        val maxPacket = config.maxPacketSize
        val bytesPerFrame = config.bytesPerFrame

        while (running.get()) {
            val buffer = try {
                bufferQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            } ?: continue

            var offset = 0
            while (offset < buffer.size && running.get()) {
                val packetSize = minOf(maxPacket, buffer.size - offset)
                val packet = buffer.copyOfRange(offset, offset + packetSize)
                val written = try {
                    conn.bulkTransfer(endpoint, packet, packetSize, BULK_TIMEOUT_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "USB bulk transfer exception: ${e.message}")
                    -1
                }

                if (written < 0) {
                    Log.e(TAG, "USB bulk transfer failed (device disconnected?)")
                    running.set(false)
                    onError()
                    return
                }

                offset += written
                if (bytesPerFrame > 0) {
                    framesWritten.addAndGet((written / bytesPerFrame).toLong())
                }
            }
        }
    }
}
