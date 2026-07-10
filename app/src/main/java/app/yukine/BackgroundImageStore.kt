package app.yukine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal interface BackgroundImageCopyStore {
    fun saveInternalCopy(context: Context, page: String, source: Uri): Uri?
    fun deleteInternalCopy(context: Context, uri: Uri?)
}

internal class BackgroundImageStore(
    private val maxStoredEdgePx: Int = 1600,
    private val jpegQuality: Int = 88
) : BackgroundImageCopyStore {
    override fun saveInternalCopy(context: Context, page: String, source: Uri): Uri? {
        val bitmap = decodeSampledBitmap(context.applicationContext, source) ?: return null
        val dir = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }
        val file = File(dir, fileNameForPage(page))
        return try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    throw IOException("Failed to compress background image")
                }
            }
            Uri.fromFile(file)
        } catch (error: IOException) {
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    override fun deleteInternalCopy(context: Context, uri: Uri?) {
        val file = internalFileForUri(context.applicationContext, uri) ?: return
        file.delete()
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var maxEdge = maxOf(width, height)
        while (maxEdge / sample > maxStoredEdgePx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun internalFileForUri(context: Context, uri: Uri?): File? {
        if (uri == null || uri.scheme?.lowercase() != "file") {
            return null
        }
        val file = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull() ?: return null
        val dir = File(context.filesDir, DIRECTORY).canonicalFile
        return file.takeIf { it.path.startsWith(dir.path + File.separator) }
    }

    private fun fileNameForPage(page: String): String {
        val safe = PageBackgrounds.normalizePage(page).ifBlank { PageBackgrounds.PAGE_ALL }
        // Compose and ArtworkLoader both use the URI as their reload/cache identity. Replacing a
        // background must therefore create a new URI instead of overwriting a fixed page file.
        return "$FILE_PREFIX$safe-${UUID.randomUUID()}$FILE_SUFFIX"
    }

    companion object {
        private const val DIRECTORY = "page_backgrounds"
        private const val FILE_PREFIX = "background_"
        private const val FILE_SUFFIX = ".jpg"
    }
}
