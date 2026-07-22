package app.yukine.playback.usb

import android.os.HandlerThread
import android.os.Handler
import android.os.Process
import app.yukine.diagnostics.DiagnosticLog
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated writer thread that transfers PCM audio data to a USB DAC.
 *
 * @param config USB audio stream configuration.
 * @param transport Endpoint-type-specific USB transport.
 * @param onError Callback invoked when a fatal USB write error occurs.
 */
internal class UsbPcmWriter(
    private val config: UsbAudioStreamConfig,
    private val transport: UsbPcmTransport,
    private val onError: (String) -> Unit,
    private val onMetrics: (UsbPcmWriterMetrics) -> Unit = {}
) {
    companion object {
        private const val TAG = "UsbPcmWriter"
        private const val QUEUE_CAPACITY = 64
        private const val STOP_JOIN_TIMEOUT_MS = 1_500L
        private const val PAUSED_POLL_INTERVAL_MS = 10L
    }

    private val bufferQueue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private val running = AtomicBoolean(false)
    private val playbackEnabled = AtomicBoolean(true)
    private val framesWritten = AtomicLong(0)
    private val queueFullCount = AtomicLong(0)
    private val underrunCount = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

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
     */
    fun start() {
        if (running.getAndSet(true)) return
        closed.set(false)
        framesWritten.set(0)
        bufferQueue.clear()

        val ht = HandlerThread("UsbPcmWriter", Process.THREAD_PRIORITY_AUDIO)
        ht.start()
        thread = ht
        handler = Handler(ht.looper)
        handler?.post(::writeLoop)
        DiagnosticLog.d(TAG, "USB PCM writer started, endpoint=0x${Integer.toHexString(config.endpointAddress)}")
    }

    /**
     * Stops the writer thread and releases resources.
     */
    fun stop() {
        running.set(false)
        if (thread == null && closed.get()) return
        bufferQueue.clear()
        transport.cancel()
        val writerThread = thread
        writerThread?.quitSafely()
        if (writerThread != null && writerThread !== Thread.currentThread()) {
            try {
                writerThread.join(STOP_JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (closed.compareAndSet(false, true)) transport.close()
        thread = null
        handler = null
        DiagnosticLog.d(TAG, "USB PCM writer stopped, totalFrames=${framesWritten.get()}")
    }

    /**
     * Queues a PCM buffer for writing to the USB DAC.
     *
     * @param pcmData Raw PCM byte array.
     * @return true if the buffer was queued, false if the queue is full or writer is stopped.
     */
    fun queueBuffer(pcmData: ByteArray): Boolean {
        if (!running.get()) return false
        val queued = bufferQueue.offer(pcmData)
        if (!queued) queueFullCount.incrementAndGet()
        // A full non-blocking sink is retried by Media3 in a tight render loop. Publishing a
        // metrics object on every rejected retry creates severe allocation/GC pressure. The
        // writer thread publishes the updated queueFullCount on its next completed transfer.
        if (queued) publishMetrics()
        return queued
    }

    /**
     * Resets the frame counter (called on flush/seek).
     */
    fun resetPosition() {
        framesWritten.set(0)
        bufferQueue.clear()
        transport.reset()
    }

    /**
     * Returns the number of buffers currently queued.
     */
    fun queuedBufferCount(): Int = bufferQueue.size

    /** Allows Media3 to prebuffer while paused without sending audio to the DAC. */
    fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled.set(enabled)
    }

    private fun writeLoop() {
        val bytesPerFrame = config.bytesPerFrame
        var receivedAudio = false
        var starving = false

        while (running.get()) {
            if (!playbackEnabled.get()) {
                try {
                    Thread.sleep(PAUSED_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }
            val buffer = try {
                bufferQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
            if (buffer == null) {
                if (receivedAudio && !starving) {
                    starving = true
                    underrunCount.incrementAndGet()
                    publishMetrics()
                }
                continue
            }
            receivedAudio = true
            starving = false

            val written = try {
                transport.write(buffer)
            } catch (e: Exception) {
                DiagnosticLog.e(TAG, "USB transfer exception: ${e.message}")
                -1
            }

            if (written != buffer.size) {
                DiagnosticLog.e(TAG, "USB transfer failed (device disconnected or rejected stream)")
                running.set(false)
                publishMetrics()
                onError(transport.metrics().lastError.ifBlank { "USB transfer failed" })
                return
            }

            if (bytesPerFrame > 0) {
                framesWritten.addAndGet((written / bytesPerFrame).toLong())
            }
            publishMetrics()
        }
    }

    private fun publishMetrics() {
        val transfer = transport.metrics()
        onMetrics(
            UsbPcmWriterMetrics(
                queueDepth = bufferQueue.size,
                submittedPackets = transfer.submittedPackets,
                completedPackets = transfer.completedPackets,
                failedPackets = transfer.failedPackets,
                queueFullCount = queueFullCount.get(),
                underruns = underrunCount.get(),
                framesWritten = framesWritten.get(),
                feedbackRateHz = transfer.feedbackRateHz,
                lastError = transfer.lastError
            )
        )
    }
}

internal data class UsbPcmWriterMetrics(
    val queueDepth: Int = 0,
    val submittedPackets: Long = 0,
    val completedPackets: Long = 0,
    val failedPackets: Long = 0,
    val queueFullCount: Long = 0,
    val underruns: Long = 0,
    val framesWritten: Long = 0,
    val feedbackRateHz: Double = 0.0,
    val lastError: String = ""
)
