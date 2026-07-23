package app.yukine.together

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal class TogetherQueueMaterializer(
    private val context: Context,
    private val sessionCache: File
) {
    fun prepare(items: List<TogetherQueueItem>): List<TogetherQueueItem> {
        sessionCache.mkdirs()
        return items.map { item ->
            require(item.shareable) { "${item.title} is not available as a local, DRM-free file" }
            val uri = Uri.parse(item.sourceUri)
            when (uri.scheme?.lowercase()) {
                null, "", "file" -> {
                    val path = if (uri.scheme == "file") uri.path.orEmpty() else item.sourceUri
                    val file = File(path)
                    require(file.isFile && file.canRead()) { "Cannot read ${item.title}" }
                    item.copy(sourceUri = file.absolutePath, sizeBytes = file.length())
                }
                "content" -> copyContent(item, uri)
                else -> error("${item.title} must be downloaded before it can be shared")
            }
        }
    }

    private fun copyContent(item: TogetherQueueItem, uri: Uri): TogetherQueueItem {
        val safeName = safeBasename(item.title.ifBlank { "audio" }) + ".audio"
        val destination = File(sessionCache, "${shortHash(item.stableId)}-$safeName")
        val temp = File(sessionCache, destination.name + ".tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open ${item.title}" }
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        }
        check(temp.renameTo(destination) || runCatching {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }.isSuccess) { "Cannot prepare ${item.title}" }
        return item.copy(sourceUri = destination.absolutePath, sizeBytes = destination.length())
    }

    private fun safeBasename(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).ifBlank { "audio" }

    private fun shortHash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
}
