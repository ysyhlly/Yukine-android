package app.yukine.together

/**
 * Canonical Together row identities and the Media3 track ids derived from them.
 *
 * Streaming rows use `streaming:{provider}:{trackId}` (same shape as [app.yukine.streaming.StreamingTrack.stableKey]
 * and [TogetherQueueItem.dedupeKey]) so catalog loads, live Media3 remaps, and native queue updates share one id space.
 * Local library rows keep the numeric library id string so remove/retain against Media3 stay direct.
 */
object TogetherStableIds {
    fun streaming(provider: String, providerTrackId: String): String =
        "streaming:$provider:$providerTrackId"

    /**
     * Maps a Together stable id onto the Media3 [app.yukine.model.Track.id] used for remove/retain.
     * Numeric library ids pass through; non-numeric (streaming keys) use a stable synthetic id.
     */
    @JvmStatic
    fun media3TrackId(stableId: String?): Long {
        if (stableId != null) {
            try {
                return stableId.toLong()
            } catch (_: NumberFormatException) {
                return -9_000_000_000L - Integer.toUnsignedLong(stableId.hashCode())
            }
        }
        return -9_000_000_000L
    }

    /** Id-only light signature used to short-circuit host queue ticks on the main thread. */
    fun lightQueueSignatureFromStableIds(stableIds: Iterable<String>): String =
        stableIds.joinToString("\u001f") { media3TrackId(it).toString() }

    fun lightQueueSignatureFromTracks(trackIds: Iterable<Long>): String =
        trackIds.joinToString("\u001f") { it.toString() }
}
