package app.yukine.together

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class TogetherReceivedFileSaver(
    private val context: Context,
    private val legacyDirectoryProvider: () -> File = {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ECHO")
    }
) {
    fun save(sourcePath: String, contentRoot: String): Result<String> = runCatching {
        val source = File(sourcePath).canonicalFile
        require(source.isFile) { "Received file is not complete" }
        val safeName = source.name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .take(180)
            .ifBlank { "together-audio" }
        val digest = contentRoot.ifBlank { sha256(source) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(source, safeName, digest)
        } else {
            saveLegacy(source, safeName, digest)
        }
    }

    private fun saveWithMediaStore(source: File, safeName: String, digest: String): String {
        val resolver = context.contentResolver
        val displayName = uniqueName(safeName, digest.take(8))
        val relativePath = "${Environment.DIRECTORY_MUSIC}/ECHO"
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=?",
            arrayOf(displayName, "$relativePath/"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                ).toString()
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/*")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Unable to create MediaStore entry" }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Unable to open MediaStore output" }
                FileInputStream(source).use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveLegacy(source: File, safeName: String, digest: String): String {
        val directory = legacyDirectoryProvider().canonicalFile
        directory.mkdirs()
        val destination = File(directory, uniqueName(safeName, digest.take(8))).canonicalFile
        require(destination.parentFile == directory) { "Unsafe destination path" }
        if (!destination.exists()) {
            val temp = File(directory, ".${destination.name}.tmp")
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            check(temp.renameTo(destination)) { "Unable to finalize received file" }
        }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), null, null)
        return destination.absolutePath
    }

    private fun uniqueName(name: String, suffix: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}-$suffix${name.substring(dot)}"
        else "$name-$suffix"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
