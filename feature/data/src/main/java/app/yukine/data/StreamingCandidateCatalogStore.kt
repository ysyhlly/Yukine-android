package app.yukine.data

import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import app.yukine.streaming.ProviderRolePolicy
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Persists a bounded runtime-search catalog without promoting playback Top1 to a confirmed source. */
class StreamingCandidateCatalogStore(
    private val database: YukineDatabase
) {
    private companion object {
        const val TARGET_TYPE = "RECORDING"
        const val MAX_CANDIDATES = 12
        const val INTERNAL_STORED_MATCH_ITEM_ID = "__stored_match__"
        val TERMINAL_STATUSES = setOf(
            "USER_CONFIRMED",
            "AUTO_CONFIRMED",
            "ALTERNATE_VERSION",
            "REJECTED",
            "EXPIRED"
        )
    }

    private val dao = database.musicIdentityDao()

    fun replace(track: Track, provider: String, candidates: List<StreamingTrack>) {
        val recordingId = dao.recordingIdForLocalTrack(track.id) ?: return
        val cleanProvider = provider.trim().lowercase(Locale.ROOT).takeIf(String::isNotBlank) ?: return
        if (!ProviderRolePolicy.canPersistCanonicalSource(cleanProvider)) return
        val ranked = StreamingTrackMatchPolicy.rankCandidates(
            StreamingTrackMatchPolicy.reference(track),
            candidates.asSequence()
                .filter { it.playable && it.providerTrackId.isNotBlank() }
                .distinctBy { it.providerTrackId.trim() }
                .toList()
        ).take(MAX_CANDIDATES)
        val ownerRecordingIds = linkedSetOf<Long>()
        database.runInTransaction {
            val activeIds = ranked.mapTo(linkedSetOf()) { it.track.providerTrackId.trim() }
            ranked.forEachIndexed { rank, match ->
                val candidate = match.track
                val providerTrackId = candidate.providerTrackId.trim()
                val existing = dao.candidate(TARGET_TYPE, recordingId, cleanProvider, providerTrackId)
                val ownedSource = dao.source(cleanProvider, providerTrackId)
                val sourceOwnerConflict = ownedSource != null && ownedSource.recordingId != recordingId
                if (sourceOwnerConflict) ownerRecordingIds += ownedSource!!.recordingId
                val status = when {
                    ownedSource?.recordingId == recordingId && ownedSource.matchStatus == "CONFIRMED" ->
                        "USER_CONFIRMED"
                    existing?.status in TERMINAL_STATUSES -> existing!!.status
                    else -> "PENDING"
                }
                val now = System.currentTimeMillis()
                dao.upsert(
                    IdentityCandidateEntity(
                        candidateId = existing?.candidateId ?: ProviderSourceIdentityWriter.candidateId(
                            recordingId,
                            cleanProvider,
                            providerTrackId
                        ),
                        targetType = TARGET_TYPE,
                        targetId = recordingId,
                        provider = cleanProvider,
                        providerItemId = providerTrackId,
                        title = candidate.title.trim(),
                        artist = candidate.artist.trim(),
                        album = candidate.album.orEmpty().trim(),
                        durationMs = candidate.durationMs?.coerceAtLeast(0L) ?: 0L,
                        isrc = candidate.isrc.orEmpty()
                            .filter(Char::isLetterOrDigit)
                            .uppercase(Locale.ROOT),
                        variantType = match.evaluation.versionType.name,
                        score = maxOf(existing?.score ?: 0.0, match.evaluation.sameRecordingProbability),
                        status = status,
                        evidenceJson = evidence(
                            rank,
                            match.evaluation,
                            sourceOwnerConflict,
                            ownedSource?.recordingId
                        ),
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                )
            }
            dao.candidates(TARGET_TYPE, recordingId)
                .asSequence()
                .filter { it.provider == cleanProvider }
                .filter { it.providerItemId != INTERNAL_STORED_MATCH_ITEM_ID }
                .filter { it.providerItemId !in activeIds }
                .filter { it.status == "PENDING" && isCatalogManaged(it.evidenceJson) }
                .forEach { dao.deleteCandidate(it.candidateId) }
        }
        if (ownerRecordingIds.isNotEmpty()) {
            SourceIdentityIngestor(database).ingestRecordings((ownerRecordingIds + recordingId).toList())
        }
    }

    private fun evidence(
        rank: Int,
        evaluation: app.yukine.streaming.MatchEvaluation,
        sourceOwnerConflict: Boolean,
        ownerRecordingId: Long?
    ): String = JSONObject()
        .put("source", "RUNTIME_SEARCH")
        .put("catalogManaged", true)
        .put("rank", rank)
        .put("primaryPlaybackCandidate", rank == 0)
        .put("scoreVersion", evaluation.scoreVersion)
        .put("similarityScore", evaluation.sameRecordingProbability)
        .put("sameRecordingProbability", evaluation.sameRecordingProbability)
        .put("sameWorkProbability", evaluation.sameWorkProbability)
        .put("relationship", evaluation.relationship.name)
        .put("recordingConfidenceCeiling", evaluation.recordingConfidenceCeiling)
        .put("workConfidenceCeiling", evaluation.workConfidenceCeiling)
        .put("titleScore", evaluation.titleScore)
        .put("artistScore", evaluation.artistScore)
        .put("durationScore", evaluation.durationScore)
        .put("versionScore", evaluation.versionScore)
        .put("albumScore", evaluation.albumScore)
        .put("variantType", evaluation.versionType.name)
        .put("hardConflict", evaluation.hasHardConflict)
        .put("hardConflicts", JSONArray().apply {
            evaluation.hardConflicts.forEach { put(it.name) }
        })
        .put("identifierEvidence", JSONArray().apply {
            evaluation.identifierEvidence.forEach(::put)
        })
        .put("explanation", JSONArray().apply {
            evaluation.explanation.forEach(::put)
        })
        .apply {
            if (sourceOwnerConflict && ownerRecordingId != null) {
                put("ownerRecordingId", ownerRecordingId)
            }
        }
        .toString()

    private fun isCatalogManaged(evidenceJson: String): Boolean = runCatching {
        JSONObject(evidenceJson).optBoolean("catalogManaged", false)
    }.getOrDefault(false)
}
