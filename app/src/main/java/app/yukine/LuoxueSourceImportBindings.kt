package app.yukine

import android.content.ContentResolver
import android.net.Uri

internal class LuoxueSourceDocumentReaderBindings(
    private val contentResolver: ContentResolver?
) : LuoxueSourceDocumentReader {
    override fun readText(uri: Uri): String? {
        val read = M3uDocumentHelper.readText(contentResolver, uri)
        return if (read == null || !read.success) null else read.text
    }
}
