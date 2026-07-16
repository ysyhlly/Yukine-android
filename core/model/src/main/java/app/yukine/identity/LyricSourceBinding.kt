package app.yukine.identity

/** Persistent provider lookup attached to one canonical recording, never to a transient source. */
data class LyricSourceBinding(
    val recordingId: Long,
    val provider: String,
    val providerLyricId: String,
    val synced: Boolean,
    val durationMs: Long,
    val checksum: String,
    val updatedAt: Long
)
