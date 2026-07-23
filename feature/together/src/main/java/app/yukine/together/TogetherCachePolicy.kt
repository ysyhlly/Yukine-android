package app.yukine.together

import java.io.File

class TogetherCachePolicy(
    private val root: File,
    private val maxBytes: Long = 2L * 1024L * 1024L * 1024L,
    private val maxCompleteAgeMs: Long = 24L * 60L * 60L * 1000L,
    private val minimumFreeBytes: Long = 512L * 1024L * 1024L
) {
    fun requireCapacity() {
        root.mkdirs()
        require(root.usableSpace >= minimumFreeBytes) {
            "At least 512 MiB of free space is required"
        }
    }

    fun trim(activeCanonicalPaths: Set<String>, nowMs: Long = System.currentTimeMillis()) {
        val active = activeCanonicalPaths.toHashSet()
        val candidates = root.walkTopDown()
            .filter(File::isFile)
            .filter { runCatching { it.canonicalPath !in active }.getOrDefault(false) }
            .sortedBy(File::lastModified)
            .toList()
        var total = root.walkTopDown().filter(File::isFile).sumOf(File::length)
        for (file in candidates) {
            val expired = nowMs - file.lastModified() > maxCompleteAgeMs
            if (!expired && total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        root.walkBottomUp().filter(File::isDirectory).filter { it != root }.forEach { dir ->
            if (dir.list().isNullOrEmpty()) dir.delete()
        }
    }
}
