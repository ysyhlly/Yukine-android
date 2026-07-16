package app.yukine.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtworkDiskCachePolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fileNameIsStableAndDoesNotExposeUrl() {
        val url = "https://music.example/cover.jpg?token=secret"
        val first = ArtworkDiskCachePolicy.fileName(url)

        assertEquals(first, ArtworkDiskCachePolicy.fileName(url))
        assertTrue(first.matches(Regex("[0-9a-f]{64}\\.img")))
        assertFalse(first.contains("secret"))
    }

    @Test
    fun trimRemovesOldestFilesFirstAndKeepsProtectedFile() {
        val directory = temporaryFolder.newFolder()
        val oldest = artworkFile(directory, "oldest.img", 4, 1)
        val protected = artworkFile(directory, "protected.img", 4, 2)
        val newest = artworkFile(directory, "newest.img", 4, 3)

        ArtworkDiskCachePolicy.trim(directory, maxBytes = 8, protectedFile = protected)

        assertFalse(oldest.exists())
        assertTrue(protected.exists())
        assertTrue(newest.exists())
    }

    private fun artworkFile(directory: File, name: String, bytes: Int, modifiedAt: Long): File {
        return File(directory, name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(modifiedAt)
        }
    }
}
