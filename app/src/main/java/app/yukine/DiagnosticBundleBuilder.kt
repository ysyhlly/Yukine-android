package app.yukine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.yukine.diagnostics.DiagnosticLog
import app.yukine.diagnostics.DiagnosticLogSnapshot
import app.yukine.diagnostics.DiagnosticRedactor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticSourceFile(
    val name: String,
    val lastModifiedMs: Long,
    val content: ByteArray,
    val truncated: Boolean = false
)

internal class DiagnosticBundleBuilder(
    private val eventSnapshotProvider: () -> DiagnosticLogSnapshot = DiagnosticLog::snapshot,
    private val crashSnapshotProvider: (Context) -> List<DiagnosticSourceFile> = ::readCrashLogs,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    fun build(context: Context, outputFile: File): Boolean = runCatching {
        val events = eventSnapshotProvider()
        val crashes = crashSnapshotProvider(context)
        val candidates = buildList {
            events.files.forEach { file ->
                add(DiagnosticSourceFile("events/${safeName(file.name)}", file.lastModifiedMs, file.content))
            }
            crashes.forEach { file ->
                add(DiagnosticSourceFile("crashes/${safeName(file.name)}", file.lastModifiedMs, file.content))
            }
        }.sortedByDescending { it.lastModifiedMs }

        var remaining = MAX_PAYLOAD_BYTES
        var truncated = crashes.any { it.truncated }
        val selected = mutableListOf<DiagnosticSourceFile>()
        candidates.forEach { source ->
            if (source.content.size.toLong() <= remaining) {
                selected += source
                remaining -= source.content.size.toLong()
            } else {
                truncated = true
            }
        }
        truncated = truncated || selected.size != candidates.size

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { output ->
            ZipOutputStream(output).use { zip ->
                putUtf8(
                    zip,
                    "diagnostics.json",
                    buildManifest(context, events, crashes.size, selected.size, truncated).toString(2)
                )
                putUtf8(zip, "README.txt", README)
                selected.sortedBy { it.name }.forEach { source ->
                    putBytes(
                        zip,
                        source.name,
                        DiagnosticRedactor.redact(source.content.toString(Charsets.UTF_8))
                            .toByteArray(Charsets.UTF_8)
                    )
                }
            }
        }
        check(outputFile.length() <= MAX_ZIP_BYTES) { "Diagnostic ZIP exceeds size limit" }
        true
    }.getOrElse {
        runCatching { outputFile.delete() }
        false
    }

    private fun buildManifest(
        context: Context,
        events: DiagnosticLogSnapshot,
        crashCount: Int,
        includedSourceCount: Int,
        truncated: Boolean
    ): JSONObject {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val runtime = Runtime.getRuntime()
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return JSONObject()
            .put("generated_at", Instant.ofEpochMilli(nowMs()).toString())
            .put("package", context.packageName)
            .put("version_name", packageInfo.versionName ?: "")
            .put("version_code", versionCode)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("abis", Build.SUPPORTED_ABIS.joinToString(","))
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
            .put("process_uptime_ms", SystemClock.elapsedRealtime())
            .put("runtime_used_bytes", runtime.totalMemory() - runtime.freeMemory())
            .put("runtime_max_bytes", runtime.maxMemory())
            .put("data_available_bytes", stat.availableBytes)
            .put("audio_permission_granted", permissionGranted(context, Manifest.permission.READ_MEDIA_AUDIO))
            .put(
                "notification_permission_granted",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    permissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            )
            .put("event_files", events.files.size)
            .put("crash_files", crashCount)
            .put("included_source_files", includedSourceCount)
            .put("dropped_events", events.droppedEvents)
            .put("event_snapshot_complete", events.complete)
            .put("truncated", truncated)
            .put("privacy", "credentials and known user identifiers redacted; databases and preferences excluded")
    }

    private fun permissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun putUtf8(zip: ZipOutputStream, name: String, value: String) =
        putBytes(zip, name, value.toByteArray(Charsets.UTF_8))

    private fun putBytes(zip: ZipOutputStream, name: String, value: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value)
        zip.closeEntry()
    }

    private fun safeName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val MAX_PAYLOAD_BYTES = 7L * 1024L * 1024L
        const val MAX_ZIP_BYTES = 8L * 1024L * 1024L
        const val MAX_CRASH_SOURCE_BYTES = 1024 * 1024
        const val MAX_CRASH_FILES = 10
        val README = """
            Yukine diagnostics package

            This archive was created only after an explicit user action. It contains bounded
            first-party runtime events, recent crash stacks, and basic app/device runtime data.
            Known credentials and identifiers are redacted again during export.

            It does not contain databases, SharedPreferences, cookies, media files, provider
            caches, or the device-wide Android Logcat.
        """.trimIndent()

        fun readCrashLogs(context: Context): List<DiagnosticSourceFile> {
            val directory = File(context.filesDir, "crash-logs")
            val files = directory.listFiles { file ->
                file.isFile && file.name.startsWith("crash-")
            }.orEmpty().sortedByDescending { it.lastModified() }
            val droppedFiles = files.size > MAX_CRASH_FILES
            return files.take(MAX_CRASH_FILES).mapIndexedNotNull { index, file ->
                val content = runCatching {
                    readPrefix(file, MAX_CRASH_SOURCE_BYTES)
                }.getOrNull() ?: return@mapIndexedNotNull null
                DiagnosticSourceFile(
                    name = file.name,
                    lastModifiedMs = file.lastModified(),
                    content = content,
                    truncated = file.length() > content.size || (index == 0 && droppedFiles)
                )
            }
        }

        private fun readPrefix(file: File, maxBytes: Int): ByteArray {
            val expectedSize = minOf(file.length(), maxBytes.toLong()).toInt()
            if (expectedSize <= 0) return byteArrayOf()
            val content = ByteArray(expectedSize)
            var offset = 0
            file.inputStream().use { input ->
                while (offset < content.size) {
                    val read = input.read(content, offset, content.size - offset)
                    if (read < 0) break
                    offset += read
                }
            }
            return if (offset == content.size) {
                content
            } else {
                content.copyOf(offset)
            }
        }
    }
}
