package app.yukine.fingerprint

import java.nio.ByteBuffer

internal object ChromaprintNative {
    data class Evidence(
        val encoded: String,
        val rawWords: IntArray
    )

    private val loaded: Boolean = runCatching {
        System.loadLibrary("chromaprint")
        System.loadLibrary("echo_chromaprint_jni")
        true
    }.getOrDefault(false)

    fun isAvailable(): Boolean = loaded

    fun version(): String = if (loaded) nativeVersion() else ""

    fun create(): Session? {
        if (!loaded) return null
        val handle = nativeCreate()
        return handle.takeIf { it != 0L }?.let(::Session)
    }

    internal class Session(private var handle: Long) : AutoCloseable {
        fun start(sampleRate: Int, channels: Int): Boolean =
            handle != 0L && nativeStart(handle, sampleRate, channels)

        fun feed(pcm16: ByteBuffer, sampleCount: Int): Boolean =
            handle != 0L && pcm16.isDirect && nativeFeed(handle, pcm16, sampleCount)

        fun finishEvidence(): Evidence? = handle.takeIf { it != 0L }
            ?.let(::nativeFinish)
            ?.let(::decodeEvidence)

        fun finish(): String? = finishEvidence()?.encoded

        override fun close() {
            val current = handle
            handle = 0L
            if (current != 0L) nativeRelease(current)
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeStart(handle: Long, sampleRate: Int, channels: Int): Boolean
    private external fun nativeFeed(handle: Long, pcm16: ByteBuffer, sampleCount: Int): Boolean
    private external fun nativeFinish(handle: Long): String?
    private external fun nativeRelease(handle: Long)
    private external fun nativeVersion(): String

    private fun decodeEvidence(payload: String): Evidence? {
        val separator = payload.indexOf('|')
        if (separator <= 0 || separator == payload.lastIndex) return null
        val encoded = payload.substring(0, separator)
        val rawHex = payload.substring(separator + 1)
        if (rawHex.length % RAW_WORD_HEX_LENGTH != 0) return null
        val rawWords = IntArray(rawHex.length / RAW_WORD_HEX_LENGTH) { index ->
            rawHex.substring(
                index * RAW_WORD_HEX_LENGTH,
                (index + 1) * RAW_WORD_HEX_LENGTH
            ).toLong(16).toInt()
        }
        return Evidence(encoded, rawWords)
    }

    private const val RAW_WORD_HEX_LENGTH = 8
}
