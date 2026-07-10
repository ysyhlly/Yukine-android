package app.yukine

/**
 * Coarse-grained device-library refresh phases. They deliberately describe work rather than UI
 * copy so data and UI layers can keep their own responsibilities.
 */
enum class LibraryRefreshPhase {
    CHECKING,
    SCANNING,
    REPLACING,
    RELOADING
}

data class LibraryRefreshProgress(
    val phase: LibraryRefreshPhase,
    val trackCount: Int = -1,
    val elapsedMs: Long = 0L
)

fun interface LibraryRefreshProgressListener {
    fun onProgress(progress: LibraryRefreshProgress)
}
