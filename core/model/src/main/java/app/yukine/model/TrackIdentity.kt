package app.yukine.model

/** Shared identity contract for persisted and playable tracks. */
object TrackIdentity {
    const val INVALID_ID: Long = -1L

    @JvmStatic
    fun isUsable(trackId: Long): Boolean = trackId != INVALID_ID

    @JvmStatic
    fun stableNegative(candidate: Long): Long =
        if (candidate == INVALID_ID || candidate == 0L) -2L else candidate
}
