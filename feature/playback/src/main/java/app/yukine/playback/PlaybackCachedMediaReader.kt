package app.yukine.playback

import app.yukine.model.Track
import java.io.File

/** Read-only boundary for media bytes already committed to the playback-owned cache. */
interface PlaybackCachedMediaReader {
    fun copyCachedPrefix(
        track: Track?,
        target: File,
        minimumBytes: Long,
        maximumBytes: Long
    ): Long
}
