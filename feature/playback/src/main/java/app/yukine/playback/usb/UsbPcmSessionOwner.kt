package app.yukine.playback.usb

/** Identity of the USB PCM session that is actually open on the DAC. */
internal data class UsbPcmSessionKey(
    val deviceId: Int,
    val sampleRateHz: Int,
    val channelCount: Int,
    val sourceBitDepth: Int,
    val usbSubslotBytes: Int,
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val clockSourceEntityId: Int
) {
    fun matchesRequest(
        deviceId: Int,
        sampleRateHz: Int,
        channelCount: Int,
        sourceBitDepth: Int
    ): Boolean = this.deviceId == deviceId &&
        this.sampleRateHz == sampleRateHz &&
        this.channelCount == channelCount &&
        this.sourceBitDepth == sourceBitDepth
}

/**
 * Owns exactly one writer and its permission-backed Android connection lifecycle.
 *
 * The owner deliberately stops the native transport before closing the Android FD. A generation
 * token prevents an asynchronous callback from an old writer from mutating the replacement
 * session after a PCM format change.
 */
internal class UsbPcmSessionOwner(
    private val closeConnection: () -> Unit
) {
    private data class ActiveSession(
        val generation: Long,
        val key: UsbPcmSessionKey,
        val writer: UsbPcmWriter
    )

    private val lock = Any()
    private var generation = 0L
    private var active: ActiveSession? = null

    fun writer(): UsbPcmWriter? = synchronized(lock) { active?.writer }

    fun activeKey(): UsbPcmSessionKey? = synchronized(lock) { active?.key }

    fun matchesRequest(
        deviceId: Int,
        sampleRateHz: Int,
        channelCount: Int,
        sourceBitDepth: Int
    ): Boolean = synchronized(lock) {
        active?.key?.matchesRequest(deviceId, sampleRateHz, channelCount, sourceBitDepth) == true
    }

    /** Stops the old writer first, closes its Android FD second, and returns a fresh generation. */
    fun beginTransition(): Long {
        val oldSession: ActiveSession?
        val nextGeneration: Long
        synchronized(lock) {
            generation += 1L
            nextGeneration = generation
            oldSession = active
            active = null
        }
        oldSession?.writer?.stop()
        closeConnection()
        return nextGeneration
    }

    fun install(generation: Long, key: UsbPcmSessionKey, writer: UsbPcmWriter): Boolean =
        synchronized(lock) {
            if (generation != this.generation || active != null) return@synchronized false
            active = ActiveSession(generation, key, writer)
            true
        }

    fun isCurrent(generation: Long, writer: UsbPcmWriter): Boolean = synchronized(lock) {
        active?.let { it.generation == generation && it.writer === writer } == true
    }

    /** Detaches only the writer that raised the callback; stale writers cannot evict a new one. */
    fun detachIfCurrent(generation: Long, writer: UsbPcmWriter): Boolean = synchronized(lock) {
        val current = active
        if (current == null || current.generation != generation || current.writer !== writer) {
            return@synchronized false
        }
        active = null
        this.generation += 1L
        true
    }

    fun closeCurrent() {
        beginTransition()
    }
}
