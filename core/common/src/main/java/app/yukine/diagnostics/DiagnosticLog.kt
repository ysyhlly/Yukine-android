package app.yukine.diagnostics

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticLogFile(
    val name: String,
    val lastModifiedMs: Long,
    val content: ByteArray
)

data class DiagnosticLogSnapshot(
    val files: List<DiagnosticLogFile>,
    val droppedEvents: Long,
    val complete: Boolean
)

/**
 * First-party diagnostic logger. It preserves Logcat behavior while retaining bounded INFO/WARN/
 * ERROR events in app-private storage for user-initiated diagnostics export.
 */
object DiagnosticLog {
    @Volatile
    private var store: RollingDiagnosticLogStore? = null

    @JvmStatic
    fun install(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store == null) {
                store = RollingDiagnosticLogStore(File(context.applicationContext.filesDir, DIR_NAME))
            }
        }
    }

    @JvmStatic
    fun d(tag: String, message: String): Int = Log.d(tag, message)

    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable): Int =
        Log.d(tag, message, throwable)

    @JvmStatic
    fun i(tag: String, message: String): Int {
        val result = Log.i(tag, message)
        store?.record("INFO", tag, message, null)
        return result
    }

    @JvmStatic
    fun i(tag: String, message: String, throwable: Throwable): Int {
        val result = Log.i(tag, message, throwable)
        store?.record("INFO", tag, message, throwable)
        return result
    }

    @JvmStatic
    fun w(tag: String, message: String): Int {
        val result = Log.w(tag, message)
        store?.record("WARN", tag, message, null)
        return result
    }

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable): Int {
        val result = Log.w(tag, message, throwable)
        store?.record("WARN", tag, message, throwable)
        return result
    }

    @JvmStatic
    fun e(tag: String, message: String): Int {
        val result = Log.e(tag, message)
        store?.record("ERROR", tag, message, null)
        return result
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable): Int {
        val result = Log.e(tag, message, throwable)
        store?.record("ERROR", tag, message, throwable)
        return result
    }

    @JvmStatic
    fun wtf(tag: String, message: String): Int {
        val result = Log.wtf(tag, message)
        store?.record("ASSERT", tag, message, null)
        return result
    }

    @JvmStatic
    fun wtf(tag: String, message: String, throwable: Throwable): Int {
        val result = Log.wtf(tag, message, throwable)
        store?.record("ASSERT", tag, message, throwable)
        return result
    }

    /** Must be called off the main thread. */
    @JvmStatic
    fun snapshot(): DiagnosticLogSnapshot = store?.snapshot()
        ?: DiagnosticLogSnapshot(emptyList(), 0L, complete = true)

    private const val DIR_NAME = "diagnostic-events"
}

internal class RollingDiagnosticLogStore(
    private val directory: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = 512L * 1024L,
    private val maxFiles: Int = 4,
    private val maxAgeMs: Long = TimeUnit.DAYS.toMillis(7)
) {
    private val droppedEvents = AtomicLong(0)
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(256),
        { runnable -> Thread(runnable, "DiagnosticLogWriter").apply { isDaemon = true } },
        { _, _ -> droppedEvents.incrementAndGet() }
    )

    init {
        directory.mkdirs()
        prune()
    }

    fun record(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestampMs = nowMs()
        val threadName = Thread.currentThread().name
        executor.execute {
            runCatching {
                append(
                    encode(timestampMs, level, tag, threadName, message, throwable)
                        .toByteArray(Charsets.UTF_8)
                )
            }.onFailure { droppedEvents.incrementAndGet() }
        }
    }

    fun snapshot(timeoutMs: Long = 2_000L): DiagnosticLogSnapshot {
        val barrier = CountDownLatch(1)
        val accepted = runCatching { executor.execute { barrier.countDown() } }.isSuccess
        val complete = accepted && runCatching {
            barrier.await(timeoutMs, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        val files = directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .mapNotNull { file ->
                runCatching {
                    DiagnosticLogFile(file.name, file.lastModified(), file.readBytes())
                }.getOrNull()
            }
        return DiagnosticLogSnapshot(files, droppedEvents.get(), complete)
    }

    private fun encode(
        timestampMs: Long,
        level: String,
        tag: String,
        threadName: String,
        message: String,
        throwable: Throwable?
    ): String {
        val json = JSONObject()
            .put("time", Instant.ofEpochMilli(timestampMs).toString())
            .put("level", level)
            .put("tag", DiagnosticRedactor.redact(tag))
            .put("thread", DiagnosticRedactor.redact(threadName))
            .put("message", DiagnosticRedactor.redact(message))
        if (throwable != null) {
            json.put("stack", DiagnosticRedactor.redact(Log.getStackTraceString(throwable)))
        }
        return json.toString() + "\n"
    }

    private fun append(bytes: ByteArray) {
        directory.mkdirs()
        var current = File(directory, CURRENT_FILE)
        if (current.exists() && current.length() + bytes.size > maxFileBytes) {
            val rotated = File(directory, "events-${nowMs()}.jsonl")
            if (!current.renameTo(rotated)) {
                current = File(directory, "events-${nowMs()}-current.jsonl")
            }
            prune()
        }
        current.appendBytes(bytes)
        prune()
    }

    private fun prune() {
        val cutoff = nowMs() - maxAgeMs
        directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            .orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
        val current = File(directory, CURRENT_FILE)
        val rotated = directory.listFiles { file ->
            file.isFile && file.extension == "jsonl" && file != current
        }.orEmpty().sortedByDescending { it.lastModified() }
        val rotatedLimit = (maxFiles - if (current.exists()) 1 else 0).coerceAtLeast(0)
        rotated.drop(rotatedLimit).forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val CURRENT_FILE = "events-current.jsonl"
    }
}
