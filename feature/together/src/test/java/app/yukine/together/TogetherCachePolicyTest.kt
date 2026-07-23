package app.yukine.together

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class TogetherCachePolicyTest {
    @Test
    fun trimNeverDeletesActiveFile() {
        val root = Files.createTempDirectory("together-cache").toFile()
        try {
            val active = root.resolve("active.part").apply { writeBytes(ByteArray(32)) }
            val stale = root.resolve("stale.bin").apply {
                writeBytes(ByteArray(32))
                setLastModified(1L)
            }
            TogetherCachePolicy(
                root = root,
                maxBytes = 32L,
                maxCompleteAgeMs = 1L,
                minimumFreeBytes = 0L
            ).trim(setOf(active.canonicalPath), nowMs = 10_000L)

            assertTrue(active.exists())
            assertFalse(stale.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
