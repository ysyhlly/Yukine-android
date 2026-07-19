package app.yukine.data

import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.RecordingVariantRecognizer
import org.json.JSONObject

internal enum class ManualMatchLabel {
    SAME,
    DIFFERENT,
    ALTERNATE
}

internal data class ManualMatchSide(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val composer: String = "",
    val releaseType: String = "",
    val year: Int = 0,
    val durationMs: Long = 0L,
    val versionSignature: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title.trim())
        .put("artist", artist.trim())
        .put("album", album.trim())
        .put("albumArtist", albumArtist.trim())
        .put("composer", composer.trim())
        .put("releaseType", releaseType.trim())
        .put("year", year.takeIf { it in 1000..9999 } ?: 0)
        .put("durationMs", durationMs.coerceAtLeast(0L))
        .put("versionSignature", versionSignature.trim())

    companion object {
        fun from(source: TrackSourceMappingEntity): ManualMatchSide = ManualMatchSide(
            title = source.title,
            artist = source.artist,
            album = source.album,
            albumArtist = source.albumArtist,
            composer = source.composer,
            releaseType = source.releaseType,
            year = source.year,
            durationMs = source.durationMs,
            versionSignature = RecordingVariantRecognizer.recognize(source.title, source.album).name
        )

        fun from(candidate: IdentityCandidateEntity): ManualMatchSide = ManualMatchSide(
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            durationMs = candidate.durationMs,
            versionSignature = candidate.variantType
        )

        fun from(recording: CanonicalRecordingEntity): ManualMatchSide = ManualMatchSide(
            title = recording.title,
            artist = recording.primaryArtistDisplay,
            album = "",
            durationMs = recording.durationMs
        )
    }
}

/**
 * Writes only explicit user decisions. Automatic clustering must never call this store.
 *
 * Payloads are deliberately whitelisted metadata: no source/provider IDs, paths, URLs, headers,
 * credentials, or response bodies are serialized.
 */
internal class ManualMatchDecisionStore(private val database: YukineDatabase) {
    private val dao get() = database.musicIdentityDao()

    fun representative(recordingId: Long): ManualMatchSide? {
        val source = dao.sources(recordingId)
            .sortedWith(
                compareByDescending<TrackSourceMappingEntity> { it.playable }
                    .thenByDescending { it.matchStatus == "CONFIRMED" }
                    .thenByDescending(TrackSourceMappingEntity::qualityScore)
                    .thenBy { it.sourceId ?: Long.MAX_VALUE }
            )
            .firstOrNull()
        return source?.let(ManualMatchSide::from)
            ?: dao.recording(recordingId)?.let(ManualMatchSide::from)
    }

    fun record(
        label: ManualMatchLabel,
        left: ManualMatchSide,
        right: ManualMatchSide,
        note: String,
        sourceRecordingId: Long?,
        targetRecordingId: Long?
    ): Long = IdentityOperationStore(database).recordAudit(
        operationType = IdentityOperationType.MANUAL_MATCH_DECISION,
        sourceRecordingId = sourceRecordingId,
        targetRecordingId = targetRecordingId,
        beforePayload = JSONObject(),
        afterPayload = JSONObject()
            .put("schemaVersion", 1)
            .put("label", label.name)
            .put("left", left.toJson())
            .put("right", right.toJson())
            .put("note", note.trim().take(MAX_NOTE_LENGTH))
    )

    private companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}
