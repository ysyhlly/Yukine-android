package app.yukine.data.enrichment

import app.yukine.data.room.YukineDatabase
import java.net.URI

class RoomMissingRecordingCoverWriter(
    private val database: YukineDatabase
) : MissingRecordingCoverWriter {
    override fun writeIfMissing(recordingId: Long, coverUrl: String, updatedAt: Long) {
        val trustedUrl = trustedCoverUrl(coverUrl)
        if (trustedUrl.isBlank()) return
        database.runInTransaction {
            database.musicIdentityDao().sources(recordingId)
                .mapNotNull { it.localTrackId }
                .distinct()
                .forEach { localTrackId ->
                    database.libraryDao().updateAlbumArtIfMissing(localTrackId, trustedUrl, updatedAt)
                }
        }
    }

    private fun trustedCoverUrl(value: String): String = runCatching {
        val uri = URI(value.trim())
        val host = uri.host?.lowercase().orEmpty()
        value.trim().takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                (host == "coverartarchive.org" || host.endsWith(".mzstatic.com"))
        }.orEmpty()
    }.getOrDefault("")
}
