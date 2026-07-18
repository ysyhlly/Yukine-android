package app.yukine.data

import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.CanonicalWorkEntity
import app.yukine.data.room.CustomLyricsEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.IdentityOperationEntity
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.LyricBindingEntity
import app.yukine.data.room.PlaybackQueueIdentityEntity
import app.yukine.data.room.PlaylistRecordingItemEntity
import app.yukine.data.room.RecordingArtistCreditEntity
import app.yukine.data.room.RecordingFavoriteEntity
import app.yukine.data.room.RecordingIdentifierEntity
import app.yukine.data.room.RecordingPlayEventEntity
import app.yukine.data.room.RecordingPlayHistoryEntity
import app.yukine.data.room.RecordingRelationEntity
import app.yukine.data.room.RecordingVariantEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import java.util.concurrent.Callable
import org.json.JSONArray
import org.json.JSONObject

data class IdentityOperation(
    val id: Long,
    val operationType: String,
    val sourceRecordingId: Long?,
    val targetRecordingId: Long?,
    val createdAt: Long,
    val revertedAt: Long?,
    val undoable: Boolean
)

internal object IdentityOperationType {
    const val CONFIRM_CANDIDATE = "CONFIRM_CANDIDATE"
    const val REJECT_CANDIDATE = "REJECT_CANDIDATE"
    const val MERGE_RECORDINGS = "MERGE_RECORDINGS"
    const val SPLIT_RECORDING = "SPLIT_RECORDING"
    const val SET_ACTIVE_SOURCE = "SET_ACTIVE_SOURCE"

    fun undoable(value: String): Boolean = value == MERGE_RECORDINGS || value == SPLIT_RECORDING
}

/**
 * Stores an exact, whitelisted identity snapshot for one-time undo.
 * Track-source paths, playback URLs, credentials, cookies and provider response bodies are never serialized.
 */
internal class IdentityOperationStore(private val database: YukineDatabase) {
    private val dao get() = database.musicIdentityDao()

    fun capture(recordingIds: Collection<Long>): IdentityStateSnapshot {
        val ids = recordingIds.filter { it > 0L }.distinct().sorted()
        val recordings = ids.mapNotNull(dao::recording).sortedBy { it.id }
        val works = recordings.mapNotNull { it.workId }
            .distinct()
            .mapNotNull(dao::work)
            .sortedBy { it.id }
        val sources = ids.flatMap(dao::sources).map(SourceState::from).sortedBy { it.sourceId }
        val identifiers = ids.flatMap(dao::identifiers)
            .sortedWith(compareBy({ it.identifierType }, { it.namespace }, { it.identifierValue }))
        val credits = ids.flatMap(dao::credits)
            .sortedWith(compareBy({ it.recordingId }, { it.position }, { it.role }, { it.artistId }))
        val variants = ids.flatMap(dao::variants)
            .sortedWith(compareBy({ it.recordingId }, { it.variantGroupId }))
        val relations = if (ids.isEmpty()) {
            emptyList()
        } else {
            dao.recordingRelations(ids)
                .distinctBy { it.leftRecordingId to it.rightRecordingId }
                .map { it.copy(evidenceJson = sanitizeSnapshotEvidence(it.evidenceJson)) }
        }
        val lyrics = ids.flatMap(dao::lyricBindings)
            .sortedWith(compareBy({ it.recordingId }, { it.provider }))
        val customLyrics = ids.flatMap(dao::customLyricsForRecording)
            .sortedWith(compareBy({ it.recordingId }, { it.identityKey }))
        val candidates = ids.flatMap { dao.candidates("RECORDING", it) }
            .map { it.copy(evidenceJson = sanitizeSnapshotEvidence(it.evidenceJson)) }
            .sortedBy { it.candidateId }
        val jobs = ids.flatMap { dao.jobs("RECORDING", it) }
            .map { it.copy(lastError = "") }
            .sortedBy { it.jobId }
        val favorites = ids.mapNotNull(database.libraryDao()::recordingFavorite).sortedBy { it.recordingId }
        val histories = ids.mapNotNull(database.historyDao()::recordingHistory).sortedBy { it.recordingId }
        val events = ids.flatMap(database.historyDao()::recordingEvents).sortedBy { it.id }
        val playlists = ids.flatMap(database.playlistDao()::playlistRecordingReferences)
            .distinctBy { it.playlistId to it.recordingId }
            .sortedWith(compareBy({ it.playlistId }, { it.recordingId }))
        val queues = if (ids.isEmpty()) emptyList() else database.playbackPersistenceDao().queueIdentities(ids)
        return IdentityStateSnapshot(
            recordingIds = ids,
            works = works,
            recordings = recordings,
            sources = sources,
            identifiers = identifiers,
            credits = credits,
            variants = variants,
            relations = relations,
            lyrics = lyrics,
            customLyrics = customLyrics,
            candidates = candidates,
            jobs = jobs,
            favorites = favorites,
            histories = histories,
            events = events,
            playlists = playlists,
            queues = queues
        )
    }

    fun recordReversible(
        operationType: String,
        sourceRecordingId: Long,
        targetRecordingId: Long,
        before: IdentityStateSnapshot
    ): Long {
        require(IdentityOperationType.undoable(operationType)) { "Operation is not reversible" }
        val after = capture(listOf(sourceRecordingId, targetRecordingId))
        return dao.insert(
            IdentityOperationEntity(
                id = null,
                operationType = operationType,
                sourceRecordingId = sourceRecordingId,
                targetRecordingId = targetRecordingId,
                beforePayload = IdentityStateSnapshotCodec.encode(before),
                afterPayload = IdentityStateSnapshotCodec.encode(after),
                createdAt = System.currentTimeMillis(),
                revertedAt = null
            )
        )
    }

    fun recordAudit(
        operationType: String,
        sourceRecordingId: Long?,
        targetRecordingId: Long?,
        beforePayload: JSONObject,
        afterPayload: JSONObject
    ): Long = dao.insert(
        IdentityOperationEntity(
            id = null,
            operationType = operationType,
            sourceRecordingId = sourceRecordingId,
            targetRecordingId = targetRecordingId,
            beforePayload = beforePayload.toString(),
            afterPayload = afterPayload.toString(),
            createdAt = System.currentTimeMillis(),
            revertedAt = null
        )
    )

    fun recordSnapshotAudit(
        operationType: String,
        recordingIds: Collection<Long>,
        before: IdentityStateSnapshot
    ): Long {
        val ids = recordingIds.filter { it > 0L }.distinct()
        return dao.insert(
            IdentityOperationEntity(
                id = null,
                operationType = operationType,
                sourceRecordingId = ids.firstOrNull(),
                targetRecordingId = ids.drop(1).firstOrNull(),
                beforePayload = IdentityStateSnapshotCodec.encode(before),
                afterPayload = IdentityStateSnapshotCodec.encode(capture(ids)),
                createdAt = System.currentTimeMillis(),
                revertedAt = null
            )
        )
    }

    fun recent(recordingId: Long, limit: Int = 10): List<IdentityOperation> =
        dao.identityOperations(recordingId, limit.coerceIn(1, 50)).mapIndexed { index, entity ->
            entity.toModel(allowUndo = index == 0)
        }

    fun undo(operationId: Long): IdentityOperation = database.runInTransaction(Callable {
        val operation = requireNotNull(dao.identityOperation(operationId)) { "Unknown identity operation $operationId" }
        require(operation.revertedAt == null) { "Identity operation was already reverted" }
        require(IdentityOperationType.undoable(operation.operationType)) { "Identity operation cannot be reverted" }
        val ids = listOfNotNull(operation.sourceRecordingId, operation.targetRecordingId).distinct()
        require(dao.newerIdentityOperationCount(operationId, ids) == 0) {
            "A newer identity operation must be reverted first"
        }
        val expectedAfter = IdentityStateSnapshotCodec.decode(operation.afterPayload)
        require(capture(ids) == expectedAfter) {
            "Recording identity changed after this operation"
        }
        restore(IdentityStateSnapshotCodec.decode(operation.beforePayload), ids)
        val revertedAt = System.currentTimeMillis()
        check(dao.markIdentityOperationReverted(operationId, revertedAt) == 1) {
            "Identity operation was reverted concurrently"
        }
        requireNotNull(dao.identityOperation(operationId)).toModel(allowUndo = false)
    })

    private fun restore(before: IdentityStateSnapshot, affectedIds: List<Long>) {
        val currentSourceIds = affectedIds.flatMap(dao::sources).mapNotNull { it.sourceId }
        val affectedSourceIds = (currentSourceIds + before.sources.map { it.sourceId }).distinct()
        dao.deleteSourceRecordingCandidates(affectedIds, affectedSourceIds)
        if (affectedSourceIds.isNotEmpty()) {
            dao.invalidateSourceCandidateGeneration(affectedSourceIds)
        }
        before.works.forEach(dao::upsert)
        val beforeIds = before.recordings.mapNotNullTo(linkedSetOf()) { it.id }
        before.recordings.filter { dao.recording(requireNotNull(it.id)) == null }.forEach { recording ->
            check(dao.insert(recording) == recording.id) { "Unable to restore recording ${recording.id}" }
        }

        before.sources.forEach { sourceState ->
            val current = requireNotNull(dao.source(sourceState.sourceId)) {
                "Source ${sourceState.sourceId} disappeared after identity operation"
            }
            check(dao.moveSourceToRecording(sourceState.sourceId, sourceState.recordingId) == 1) {
                "Source ${sourceState.sourceId} disappeared while restoring identity state"
            }
        }

        affectedIds.forEach { recordingId ->
            dao.deleteRecordingRelations(recordingId)
            dao.deleteIdentifiers(recordingId)
            dao.deleteCredits(recordingId)
            dao.deleteVariants(recordingId)
            dao.deleteLyricBindings(recordingId)
            dao.deleteCustomLyricsForRecording(recordingId)
            dao.deleteCandidates("RECORDING", recordingId)
            dao.deleteJobs("RECORDING", recordingId)
            database.libraryDao().deleteRecordingFavorite(recordingId)
            database.historyDao().deleteRecordingHistory(recordingId)
            database.playlistDao().playlistRecordingReferences(recordingId).forEach {
                database.playlistDao().removePlaylistRecordingItem(it.playlistId, recordingId)
            }
        }
        database.historyDao().deleteRecordingEvents(affectedIds)
        database.playbackPersistenceDao().deleteQueueIdentities(affectedIds)

        before.identifiers.forEach(dao::upsert)
        before.credits.forEach(dao::upsert)
        before.variants.forEach(dao::upsert)
        if (before.relations.isNotEmpty()) dao.upsertRecordingRelations(before.relations)
        before.lyrics.forEach(dao::upsert)
        before.customLyrics.forEach(dao::upsert)
        before.candidates.forEach(dao::upsert)
        before.jobs.forEach(dao::upsert)
        before.favorites.forEach(database.libraryDao()::putRecordingFavorite)
        before.histories.forEach(database.historyDao()::upsertRecordingHistory)
        if (before.events.isNotEmpty()) database.historyDao().upsertRecordingEvents(before.events)
        before.playlists.forEach(database.playlistDao()::upsertPlaylistRecordingItem)
        if (before.queues.isNotEmpty()) database.playbackPersistenceDao().insertQueueIdentities(before.queues)

        before.recordings.forEach { recording ->
            check(dao.update(recording) == 1) { "Unable to restore recording ${recording.id}" }
        }
        affectedIds.filterNot(beforeIds::contains).forEach { extraId ->
            check(dao.deleteRecording(extraId) == 1) { "Unable to remove split recording $extraId" }
        }
    }
}

private fun IdentityOperationEntity.toModel(allowUndo: Boolean) = IdentityOperation(
    id = requireNotNull(id),
    operationType = operationType,
    sourceRecordingId = sourceRecordingId,
    targetRecordingId = targetRecordingId,
    createdAt = createdAt,
    revertedAt = revertedAt,
    undoable = allowUndo && revertedAt == null && IdentityOperationType.undoable(operationType)
)

internal data class SourceState(
    val sourceId: Long,
    val recordingId: Long,
    val provider: String,
    val providerTrackId: String,
    val localTrackId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val quality: String,
    val qualityScore: Int,
    val playable: Boolean,
    val matchStatus: String,
    val confidence: Double,
    val lastSuccessfulAt: Long,
    val lastVerifiedAt: Long,
    val legacyLocalKey: String,
    val codec: String,
    val bitrateKbps: Int,
    val lastFailureAt: Long,
    val failureReason: String,
    val failureCount: Int
) {
    companion object {
        fun from(value: TrackSourceMappingEntity) = SourceState(
            sourceId = requireNotNull(value.sourceId),
            recordingId = value.recordingId,
            provider = value.provider,
            providerTrackId = sanitizeSnapshotText(value.providerTrackId),
            localTrackId = value.localTrackId,
            title = value.title,
            artist = value.artist,
            album = value.album,
            durationMs = value.durationMs,
            quality = value.quality,
            qualityScore = value.qualityScore,
            playable = value.playable,
            matchStatus = value.matchStatus,
            confidence = value.confidence,
            lastSuccessfulAt = value.lastSuccessfulAt,
            lastVerifiedAt = value.lastVerifiedAt,
            legacyLocalKey = "",
            codec = value.codec,
            bitrateKbps = value.bitrateKbps,
            lastFailureAt = value.lastFailureAt,
            failureReason = sanitizeSnapshotText(value.failureReason),
            failureCount = value.failureCount
        )
    }
}

private fun sanitizeSnapshotEvidence(value: String): String {
    if (value.isBlank()) return ""
    return runCatching { sanitizeSnapshotJson(JSONObject(value)).toString() }
        .getOrElse { sanitizeSnapshotText(value) }
}

private fun sanitizeSnapshotJson(value: JSONObject): JSONObject = JSONObject().also { safe ->
    value.keys().forEach { key ->
        val raw = value.opt(key)
        safe.put(
            key,
            if (key.isSensitiveSnapshotKey()) "[redacted]" else when (raw) {
                is JSONObject -> sanitizeSnapshotJson(raw)
                is JSONArray -> sanitizeSnapshotArray(raw)
                is String -> sanitizeSnapshotText(raw)
                else -> raw
            }
        )
    }
}

private fun sanitizeSnapshotArray(value: JSONArray): JSONArray = JSONArray().also { safe ->
    for (index in 0 until value.length()) {
        safe.put(
            when (val raw = value.opt(index)) {
                is JSONObject -> sanitizeSnapshotJson(raw)
                is JSONArray -> sanitizeSnapshotArray(raw)
                is String -> sanitizeSnapshotText(raw)
                else -> raw
            }
        )
    }
}

private fun String.isSensitiveSnapshotKey(): Boolean {
    val normalized = lowercase().replace("_", "").replace("-", "")
    return SENSITIVE_SNAPSHOT_KEYS.any(normalized::contains)
}

private fun sanitizeSnapshotText(value: String): String = value
    .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
    .replace(
        Regex("(?i)(authorization|cookie|token|password|secret)\\s*[:=]\\s*\\S+"),
        "$1=[redacted]"
    )
    .take(2_000)

private val SENSITIVE_SNAPSHOT_KEYS = setOf(
    "url", "uri", "cookie", "authorization", "token", "password", "secret", "header", "response", "body"
)

internal data class IdentityStateSnapshot(
    val recordingIds: List<Long>,
    val works: List<CanonicalWorkEntity>,
    val recordings: List<CanonicalRecordingEntity>,
    val sources: List<SourceState>,
    val identifiers: List<RecordingIdentifierEntity>,
    val credits: List<RecordingArtistCreditEntity>,
    val variants: List<RecordingVariantEntity>,
    val relations: List<RecordingRelationEntity>,
    val lyrics: List<LyricBindingEntity>,
    val customLyrics: List<CustomLyricsEntity>,
    val candidates: List<IdentityCandidateEntity>,
    val jobs: List<IdentityResolutionJobEntity>,
    val favorites: List<RecordingFavoriteEntity>,
    val histories: List<RecordingPlayHistoryEntity>,
    val events: List<RecordingPlayEventEntity>,
    val playlists: List<PlaylistRecordingItemEntity>,
    val queues: List<PlaybackQueueIdentityEntity>
)

private object IdentityStateSnapshotCodec {
    fun encode(value: IdentityStateSnapshot): String = JSONObject()
        .put("version", 1)
        .put("recordingIds", array(value.recordingIds) { JSONObject().put("id", it) })
        .put("works", array(value.works, ::workJson))
        .put("recordings", array(value.recordings, ::recordingJson))
        .put("sources", array(value.sources, ::sourceJson))
        .put("identifiers", array(value.identifiers, ::identifierJson))
        .put("credits", array(value.credits, ::creditJson))
        .put("variants", array(value.variants, ::variantJson))
        .put("relations", array(value.relations, ::relationJson))
        .put("lyrics", array(value.lyrics, ::lyricJson))
        .put("customLyrics", array(value.customLyrics, ::customLyricsJson))
        .put("candidates", array(value.candidates, ::candidateJson))
        .put("jobs", array(value.jobs, ::jobJson))
        .put("favorites", array(value.favorites, ::favoriteJson))
        .put("histories", array(value.histories, ::historyJson))
        .put("events", array(value.events, ::eventJson))
        .put("playlists", array(value.playlists, ::playlistJson))
        .put("queues", array(value.queues, ::queueJson))
        .toString()

    fun decode(raw: String): IdentityStateSnapshot {
        val json = JSONObject(raw)
        require(json.optInt("version") == 1) { "Unsupported identity operation snapshot" }
        return IdentityStateSnapshot(
            recordingIds = list(json, "recordingIds") { it.getLong("id") },
            works = optionalList(json, "works", ::work),
            recordings = list(json, "recordings", ::recording),
            sources = list(json, "sources", ::source),
            identifiers = list(json, "identifiers", ::identifier),
            credits = list(json, "credits", ::credit),
            variants = list(json, "variants", ::variant),
            relations = optionalList(json, "relations", ::relation),
            lyrics = list(json, "lyrics", ::lyric),
            customLyrics = optionalList(json, "customLyrics", ::customLyrics),
            candidates = list(json, "candidates", ::candidate),
            jobs = list(json, "jobs", ::job),
            favorites = list(json, "favorites", ::favorite),
            histories = list(json, "histories", ::history),
            events = list(json, "events", ::event),
            playlists = list(json, "playlists", ::playlist),
            queues = list(json, "queues", ::queue)
        )
    }

    private fun workJson(v: CanonicalWorkEntity) = JSONObject()
        .put("id", v.id)
        .put("uuid", v.canonicalUuid)
        .put("title", v.normalizedTitle)
        .putNullable("creator", v.primaryCreatorId)
        .put("created", v.createdAt)
        .put("updated", v.updatedAt)

    private fun work(j: JSONObject) = CanonicalWorkEntity(
        j.getLong("id"),
        j.getString("uuid"),
        j.getString("title"),
        j.nullableLong("creator"),
        j.getLong("created"),
        j.getLong("updated")
    )

    private fun recordingJson(v: CanonicalRecordingEntity) = JSONObject()
        .put("id", v.id).put("uuid", v.canonicalUuid).putNullable("workId", v.workId)
        .putNullable("active", v.activeSourceId)
        .put("mbid", v.musicBrainzRecordingId).put("work", v.musicBrainzWorkId).put("title", v.title)
        .put("artist", v.primaryArtistDisplay).put("duration", v.durationMs).put("isrc", v.isrc)
        .put("acoust", v.acoustId).put("status", v.matchStatus).put("confidence", v.confidence)
        .put("metadata", v.metadataSource).put("created", v.createdAt).put("updated", v.updatedAt)

    private fun recording(j: JSONObject) = CanonicalRecordingEntity(
        j.getLong("id"), j.getString("uuid"), j.nullableLong("workId"),
        j.nullableLong("active"), j.getString("mbid"),
        j.getString("work"), j.getString("title"), j.getString("artist"), j.getLong("duration"),
        j.getString("isrc"), j.getString("acoust"), j.getString("status"), j.getDouble("confidence"),
        j.getString("metadata"), j.getLong("created"), j.getLong("updated")
    )

    private fun sourceJson(v: SourceState) = JSONObject().put("id", v.sourceId).put("recording", v.recordingId)
        .put("provider", v.provider).put("providerId", v.providerTrackId).putNullable("local", v.localTrackId)
        .put("title", v.title).put("artist", v.artist).put("album", v.album).put("duration", v.durationMs)
        .put("quality", v.quality).put("qualityScore", v.qualityScore).put("playable", v.playable)
        .put("status", v.matchStatus).put("confidence", v.confidence).put("success", v.lastSuccessfulAt)
        .put("verified", v.lastVerifiedAt).put("legacy", v.legacyLocalKey).put("codec", v.codec)
        .put("bitrate", v.bitrateKbps).put("failureAt", v.lastFailureAt)
        .put("failureReason", v.failureReason).put("failureCount", v.failureCount)

    private fun source(j: JSONObject) = SourceState(
        j.getLong("id"), j.getLong("recording"), j.getString("provider"), j.getString("providerId"),
        j.nullableLong("local"), j.getString("title"), j.getString("artist"), j.getString("album"),
        j.getLong("duration"), j.getString("quality"), j.getInt("qualityScore"), j.getBoolean("playable"),
        j.getString("status"), j.getDouble("confidence"), j.getLong("success"), j.getLong("verified"),
        j.getString("legacy"), j.optString("codec"), j.optInt("bitrate"), j.optLong("failureAt"),
        j.optString("failureReason"), j.optInt("failureCount")
    )

    private fun identifierJson(v: RecordingIdentifierEntity) = JSONObject().put("recording", v.recordingId)
        .put("type", v.identifierType).put("namespace", v.namespace).put("value", v.identifierValue)
        .put("source", v.source).put("confidence", v.confidence).put("verified", v.verifiedAt)
    private fun identifier(j: JSONObject) = RecordingIdentifierEntity(j.getLong("recording"), j.getString("type"), j.getString("namespace"), j.getString("value"), j.getString("source"), j.getDouble("confidence"), j.getLong("verified"))

    private fun creditJson(v: RecordingArtistCreditEntity) = JSONObject().put("recording", v.recordingId)
        .put("artist", v.artistId).put("role", v.role).put("position", v.position).put("name", v.creditedName)
        .put("join", v.joinPhrase).put("confidence", v.confidence)
    private fun credit(j: JSONObject) = RecordingArtistCreditEntity(j.getLong("recording"), j.getLong("artist"), j.getString("role"), j.getInt("position"), j.getString("name"), j.getString("join"), j.getDouble("confidence"))

    private fun variantJson(v: RecordingVariantEntity) = JSONObject().put("group", v.variantGroupId)
        .put("recording", v.recordingId).put("type", v.variantType).put("name", v.displayName).put("confidence", v.confidence)
    private fun variant(j: JSONObject) = RecordingVariantEntity(j.getString("group"), j.getLong("recording"), j.getString("type"), j.getString("name"), j.getDouble("confidence"))

    private fun relationJson(v: RecordingRelationEntity) = JSONObject()
        .put("left", v.leftRecordingId).put("right", v.rightRecordingId)
        .put("type", v.relationType).put("sameRecording", v.sameRecordingProbability)
        .put("sameWork", v.sameWorkProbability).put("confidence", v.confidence)
        .put("origin", v.origin).put("algorithm", v.algorithmVersion)
        .put("evidence", v.evidenceJson).put("locked", v.locked)
        .put("created", v.createdAt).put("updated", v.updatedAt)
    private fun relation(j: JSONObject) = RecordingRelationEntity(
        j.getLong("left"), j.getLong("right"), j.getString("type"),
        j.getDouble("sameRecording"), j.getDouble("sameWork"), j.getDouble("confidence"),
        j.getString("origin"), j.getInt("algorithm"), j.getString("evidence"),
        j.getBoolean("locked"), j.getLong("created"), j.getLong("updated")
    )

    private fun lyricJson(v: LyricBindingEntity) = JSONObject().put("recording", v.recordingId).put("provider", v.provider)
        .put("providerId", v.providerLyricId).put("synced", v.synced).put("duration", v.durationMs)
        .put("checksum", v.checksum).put("updated", v.updatedAt)
    private fun lyric(j: JSONObject) = LyricBindingEntity(j.getLong("recording"), j.getString("provider"), j.getString("providerId"), j.getBoolean("synced"), j.getLong("duration"), j.getString("checksum"), j.getLong("updated"))

    private fun customLyricsJson(v: CustomLyricsEntity) = JSONObject()
        .put("identityKey", v.identityKey)
        .putNullable("recording", v.recordingId)
        .put("provider", v.provider)
        .put("providerId", v.providerTrackId)
        .put("sourceName", v.sourceName)
        .put("format", v.format)
        .put("document", v.documentJson)
        .put("checksum", v.checksum)
        .put("updated", v.updatedAt)
    private fun customLyrics(j: JSONObject) = CustomLyricsEntity(
        identityKey = j.getString("identityKey"),
        recordingId = j.nullableLong("recording"),
        provider = j.optString("provider"),
        providerTrackId = j.optString("providerId"),
        sourceName = j.optString("sourceName"),
        format = j.optString("format"),
        documentJson = j.getString("document"),
        checksum = j.optString("checksum"),
        updatedAt = j.getLong("updated")
    )

    private fun candidateJson(v: IdentityCandidateEntity) = JSONObject().put("id", v.candidateId).put("targetType", v.targetType)
        .put("target", v.targetId).put("provider", v.provider).put("providerId", v.providerItemId).put("title", v.title)
        .put("artist", v.artist).put("album", v.album).put("duration", v.durationMs).put("isrc", v.isrc)
        .put("variant", v.variantType).put("score", v.score).put("status", v.status).put("evidence", v.evidenceJson)
        .put("created", v.createdAt).put("updated", v.updatedAt)
    private fun candidate(j: JSONObject) = IdentityCandidateEntity(j.getString("id"), j.getString("targetType"), j.getLong("target"), j.getString("provider"), j.getString("providerId"), j.getString("title"), j.getString("artist"), j.getString("album"), j.getLong("duration"), j.getString("isrc"), j.getString("variant"), j.getDouble("score"), j.getString("status"), j.getString("evidence"), j.getLong("created"), j.getLong("updated"))

    private fun jobJson(v: IdentityResolutionJobEntity) = JSONObject().put("id", v.jobId).put("targetType", v.targetType)
        .put("target", v.targetId).put("priority", v.priority).put("reason", v.reason).put("attempts", v.attemptCount)
        .put("next", v.nextAttemptAt).put("status", v.status).put("created", v.createdAt).put("updated", v.updatedAt)
    private fun job(j: JSONObject) = IdentityResolutionJobEntity(j.getString("id"), j.getString("targetType"), j.getLong("target"), j.getInt("priority"), j.getString("reason"), j.getInt("attempts"), j.getLong("next"), "", j.getString("status"), j.getLong("created"), j.getLong("updated"))

    private fun favoriteJson(v: RecordingFavoriteEntity) = JSONObject().put("recording", v.recordingId)
        .put("created", v.createdAt).put("syncState", v.syncState)
    private fun favorite(j: JSONObject) = RecordingFavoriteEntity(
        j.getLong("recording"),
        j.getLong("created"),
        j.optString("syncState", "LOCAL_ONLY")
    )
    private fun historyJson(v: RecordingPlayHistoryEntity) = JSONObject().put("recording", v.recordingId).put("track", v.representativeTrackId).put("played", v.playedAt).put("count", v.playCount)
    private fun history(j: JSONObject) = RecordingPlayHistoryEntity(j.getLong("recording"), j.getLong("track"), j.getLong("played"), j.getInt("count"))
    private fun eventJson(v: RecordingPlayEventEntity) = JSONObject().put("id", v.id).put("recording", v.recordingId).putNullable("source", v.sourceId).put("track", v.trackIdSnapshot).put("played", v.playedAt).putNullable("legacyEvent", v.legacyEventId)
    private fun event(j: JSONObject) = RecordingPlayEventEntity(j.getLong("id"), j.getLong("recording"), j.nullableLong("source"), j.getLong("track"), j.getLong("played"), j.nullableLong("legacyEvent"))
    private fun playlistJson(v: PlaylistRecordingItemEntity) = JSONObject().put("playlist", v.playlistId).put("recording", v.recordingId).put("track", v.representativeTrackId).put("sort", v.sortKey).put("added", v.addedAt)
    private fun playlist(j: JSONObject) = PlaylistRecordingItemEntity(j.getLong("playlist"), j.getLong("recording"), j.getLong("track"), j.getLong("sort"), j.getLong("added"))
    private fun queueJson(v: PlaybackQueueIdentityEntity) = JSONObject().put("position", v.position).put("recording", v.recordingId).putNullable("source", v.preferredSourceId)
    private fun queue(j: JSONObject) = PlaybackQueueIdentityEntity(j.getInt("position"), j.getLong("recording"), j.nullableLong("source"))

    private fun <T> array(values: List<T>, mapper: (T) -> JSONObject) = JSONArray().also { array ->
        values.forEach { array.put(mapper(it)) }
    }
    private fun <T> list(parent: JSONObject, key: String, mapper: (JSONObject) -> T): List<T> {
        val array = parent.getJSONArray(key)
        return (0 until array.length()).map { mapper(array.getJSONObject(it)) }
    }
    private fun <T> optionalList(parent: JSONObject, key: String, mapper: (JSONObject) -> T): List<T> {
        val array = parent.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { mapper(array.getJSONObject(it)) }
    }
    private fun JSONObject.putNullable(key: String, value: Long?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
