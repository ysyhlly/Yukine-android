package app.yukine.together

import android.content.Context
import android.net.Uri
import app.yukine.diagnostics.DiagnosticLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class TogetherQueueMaterializer(
    private val context: Context,
    private val sessionCache: File,
    private val streamingResolver: TogetherStreamingResolverPort = EmptyTogetherStreamingResolver
) {
    fun prepare(items: List<TogetherQueueItem>): List<TogetherQueueItem> {
        sessionCache.mkdirs()
        val failures = ArrayList<Exception>()
        val prepared = items.mapNotNull { item ->
            try {
                prepareItem(item)
            } catch (error: Exception) {
                failures += error
                runCatching {
                    DiagnosticLog.w(
                        TAG,
                        "Skipping Together queue item kind=${sourceKind(item)} reason=${safeReason(item, error)}"
                    )
                }
                null
            }
        }
        if (items.isNotEmpty() && prepared.isEmpty()) {
            throw failures.firstOrNull()
                ?: IllegalArgumentException("一起听队列中没有可用歌曲")
        }
        return prepared
    }

    private fun prepareItem(item: TogetherQueueItem): TogetherQueueItem {
        val streaming = item.source as? TogetherQueueSource.Streaming
        if (streaming != null) {
            require(item.shareable) { "${item.title} is not shareable" }
            require(streaming.provider.isNotBlank() && streaming.providerTrackId.isNotBlank()) {
                "${item.title} has no stable streaming identity"
            }
            val resolved = if (
                streaming.resolvedUrl.isNotBlank() &&
                streaming.supportsRange &&
                (streaming.sizeBytes > 0L || item.sizeBytes > 0L)
            ) {
                streaming
            } else {
                streamingResolver.resolve(streaming)
                    ?: streaming.takeIf { it.resolvedUrl.isNotBlank() }
            }
            val sizeBytes = resolved?.sizeBytes?.takeIf { it > 0L } ?: item.sizeBytes
            require(resolved != null && resolved.resolvedUrl.isNotBlank()) {
                "${item.title} 的云端音源暂时不可用"
            }
            if (resolved.supportsRange && sizeBytes > 0L) {
                return item.copy(
                    sourceUri = resolved.resolvedUrl,
                    sizeBytes = sizeBytes,
                    source = resolved.copy(sizeBytes = sizeBytes)
                )
            }
            return downloadStreaming(item, resolved)
        }
        require(item.shareable) { "${item.title} is not available as a local, DRM-free file" }
        val uri = Uri.parse(item.sourceUri)
        return when (uri.scheme?.lowercase()) {
            null, "", "file" -> {
                val path = if (uri.scheme == "file") uri.path.orEmpty() else item.sourceUri
                val file = File(path)
                require(file.isFile && file.canRead()) { "Cannot read ${item.title}" }
                item.copy(
                    sourceUri = file.absolutePath,
                    sizeBytes = file.length(),
                    source = TogetherQueueSource.Local(file.absolutePath, file.length(), item.contentRoot)
                )
            }
            "content" -> copyContent(item, uri)
            else -> error("${item.title} must be downloaded before it can be shared")
        }
    }

    private fun sourceKind(item: TogetherQueueItem): String = when (item.source) {
        is TogetherQueueSource.Local -> "local"
        is TogetherQueueSource.Streaming -> "streaming"
    }

    private fun safeReason(item: TogetherQueueItem, error: Exception): String {
        val message = error.message.orEmpty()
        val redactedTitle = if (item.title.isBlank()) message else message.replace(item.title, "<track>")
        return redactedTitle.lineSequence()
            .firstOrNull()
            .orEmpty()
            .take(240)
            .ifBlank { error.javaClass.simpleName }
    }

    private fun downloadStreaming(
        item: TogetherQueueItem,
        source: TogetherQueueSource.Streaming
    ): TogetherQueueItem {
        val url = URL(source.resolvedUrl)
        require(url.protocol.equals("http", ignoreCase = true) ||
            url.protocol.equals("https", ignoreCase = true)) {
            "${item.title} 的云端音源不是 HTTP 地址"
        }
        val safeName = safeBasename(item.title.ifBlank { "audio" }) + ".audio"
        val cacheKey = "${item.stableId}:${source.quality}"
        val destination = File(sessionCache, "${shortHash(cacheKey)}-$safeName")
        if (destination.isFile && destination.length() > 0L) {
            return localItem(item, destination)
        }

        val temp = File(sessionCache, destination.name + ".tmp")
        temp.delete()
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = STREAM_CONNECT_TIMEOUT_MS
                readTimeout = STREAM_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                source.headers.forEach { (name, value) ->
                    if (name.isNotBlank()) setRequestProperty(name, value)
                }
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept", "audio/*,*/*;q=0.8")
            }
            connection.connect()
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "${item.title} 的云端音源下载失败（HTTP ${connection.responseCode}）"
            }
            val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
            require(declaredLength <= MAX_STREAMING_FALLBACK_BYTES) {
                "${item.title} 超过一起听单曲缓存上限"
            }
            val copiedBytes = connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    copyWithLimit(item.title, input, output)
                }
            }
            require(copiedBytes > 0L) { "${item.title} 的云端音源为空" }
            require(declaredLength < 0L || copiedBytes == declaredLength) {
                "${item.title} 的云端音源下载不完整"
            }
            commitTemp(temp, destination, item.title)
            return localItem(item, destination)
        } finally {
            connection?.disconnect()
            temp.delete()
        }
    }

    private fun copyWithLimit(
        title: String,
        input: InputStream,
        output: FileOutputStream
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= MAX_STREAMING_FALLBACK_BYTES) {
                "$title 超过一起听单曲缓存上限"
            }
            output.write(buffer, 0, count)
        }
        output.fd.sync()
        return total
    }

    private fun commitTemp(temp: File, destination: File, title: String) {
        check(temp.renameTo(destination) || runCatching {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }.isSuccess) { "无法为一起听缓存 $title" }
    }

    private fun localItem(item: TogetherQueueItem, file: File): TogetherQueueItem =
        item.copy(
            sourceUri = file.absolutePath,
            sizeBytes = file.length(),
            contentRoot = "",
            source = TogetherQueueSource.Local(file.absolutePath, file.length())
        )

    private fun copyContent(item: TogetherQueueItem, uri: Uri): TogetherQueueItem {
        val safeName = safeBasename(item.title.ifBlank { "audio" }) + ".audio"
        val destination = File(sessionCache, "${shortHash(item.stableId)}-$safeName")
        val temp = File(sessionCache, destination.name + ".tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open ${item.title}" }
                FileOutputStream(temp).use { output ->
                    copyWithLimit(item.title, input, output)
                }
            }
            check(temp.renameTo(destination) || runCatching {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }.isSuccess) { "Cannot prepare ${item.title}" }
            return item.copy(
                sourceUri = destination.absolutePath,
                sizeBytes = destination.length(),
                source = TogetherQueueSource.Local(
                    destination.absolutePath,
                    destination.length(),
                    item.contentRoot
                )
            )
        } finally {
            temp.delete()
        }
    }

    private fun safeBasename(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).ifBlank { "audio" }

    private fun shortHash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "TogetherMaterializer"
        const val STREAM_CONNECT_TIMEOUT_MS = 15_000
        const val STREAM_READ_TIMEOUT_MS = 30_000
        /** Cap full-body fallback downloads so one non-range track cannot stall create for hundreds of MB. */
        const val MAX_STREAMING_FALLBACK_BYTES = 80L * 1024L * 1024L
    }
}
