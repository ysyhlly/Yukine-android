package app.yukine.data

import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.MusicIdentityDao
import app.yukine.data.room.RecordingIdentifierEntity
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityMatchStatus
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.RecordingIdentityRepository
import app.yukine.identity.RecordingVariantRecognizer
import app.yukine.identity.TrackSourceMapping
import app.yukine.model.Track
import app.yukine.streaming.RecordingRelationship
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable
import kotlin.math.max

class RoomRecordingIdentityRepository(
    private val database: YukineDatabase
) : RecordingIdentityRepository {
    private val dao: MusicIdentityDao
        get() = database.musicIdentityDao()

    override fun ensureCanonicalForTrack(track: Track): CanonicalRecording {
        val now = System.currentTimeMillis()
        database.runInTransaction {
            val entity = TrackEntityMapper.entity(track, now)
            database.libraryDao().upsertTracks(listOf(entity))
            OfflineMusicIdentityStore(database).ensureTracks(listOf(entity), now)
        }
        SourceIdentityIngestor(database).ingestLocalTracks(listOf(track.id))
        val recordingId = requireNotNull(dao.recordingIdForLocalTrack(track.id)) {
            "Track ${track.id} has no recording identity after persistence"
        }
        return requireRecording(recordingId).toModel()
    }

    override fun canonicalForRecording(recordingId: Long): CanonicalRecording? =
        dao.recording(recordingId)?.toModel()

    override fun canonicalForUuid(canonicalId: String): CanonicalRecording? =
        dao.canonicalRecording(canonicalId.trim())?.toModel()

    override fun canonicalForLocalTrack(trackId: Long): CanonicalRecording? =
        dao.recordingIdForLocalTrack(trackId)?.let(dao::recording)?.toModel()

    override fun canonicalForProviderTrack(
        provider: String,
        providerTrackId: String
    ): CanonicalRecording? = dao.recordingForProvider(
        normalizeProvider(provider),
        providerTrackId.trim()
    )?.toModel()

    override fun confirmedSources(recordingId: Long): List<TrackSourceMapping> {
        val recording = dao.recording(recordingId) ?: return emptyList()
        return dao.sources(recordingId)
            .asSequence()
            .filter { it.playable && it.matchStatus == IdentityMatchStatus.CONFIRMED.name }
            .sortedWith(
                compareByDescending<app.yukine.data.room.TrackSourceMappingEntity> {
                    sourcePriority(it.provider)
                }.thenByDescending { it.qualityScore }
                    .thenByDescending { it.lastSuccessfulAt }
                    .thenBy { it.sourceId }
            )
            .map { it.toModel(recording) }
            .toList()
    }

    override fun attachIdentifier(recordingId: Long, identifier: RecordingIdentifier) {
        database.runInTransaction {
            val recording = requireRecording(recordingId)
            require(identifier.recordingId == recordingId) { "Identifier recording ID mismatch" }
            require(identifier.canonicalId == recording.canonicalUuid) { "Identifier UUID mismatch" }
            val type = identifier.identifierType.trim().uppercase(Locale.ROOT)
            val namespace = identifier.namespace.trim()
            val value = normalizeIdentifier(type, identifier.identifierValue)
            require(type.isNotBlank() && value.isNotBlank()) { "Identifier type and value are required" }
            val existing = dao.identifier(type, namespace, value)
            require(existing == null || existing.recordingId == recordingId) {
                "Identifier already belongs to another recording"
            }
            val updated = withIdentifier(recording, type, value)
            dao.update(updated.copy(updatedAt = max(recording.updatedAt, identifier.verifiedAt)))
            dao.upsert(
                RecordingIdentifierEntity(
                    recordingId = recordingId,
                    identifierType = type,
                    namespace = namespace,
                    identifierValue = value,
                    source = identifier.source.trim(),
                    confidence = identifier.confidence.coerceIn(0.0, 1.0),
                    verifiedAt = identifier.verifiedAt
                )
            )
        }
    }

    override fun mergeRecordings(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): CanonicalRecording {
        val preflightSource = requireRecording(sourceRecordingId)
        val preflightTarget = requireRecording(targetRecordingId)
        if (differentStrongWork(preflightSource, preflightTarget)) {
            database.runInTransaction {
                RecordingRelationStore(database).upsert(
                    listOf(
                        RecordingRelationDraft(
                            leftRecordingId = sourceRecordingId,
                            rightRecordingId = targetRecordingId,
                            relationship = app.yukine.streaming.RecordingRelationship.CANNOT_LINK,
                            sameRecordingProbability = 0.0,
                            sameWorkProbability = 0.0,
                            confidence = 1.0,
                            origin = "WORK_ID_CONFLICT",
                            evidenceJson = "{\"hardConflict\":\"WORK_MBID\"}"
                        )
                    )
                )
            }
            throw IllegalArgumentException("Work MBID conflict")
        }
        return database.runInTransaction(Callable {
            mergeRecordingsInTransaction(sourceRecordingId, targetRecordingId)
        })
    }

    /**
     * Performs the merge on the caller's current transaction.
     *
     * Candidate confirmation already owns the transaction that must atomically cover both the
     * recording merge and the candidate status update. Starting another Room transaction there
     * can strand the only rollback-journal connection, so that path reuses this implementation.
     */
    fun mergeRecordingsInCurrentTransaction(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): CanonicalRecording = mergeRecordingsInTransaction(sourceRecordingId, targetRecordingId)

    internal fun mergeRecordingsInTransaction(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): CanonicalRecording {
        check(database.inTransaction()) { "Recording merge requires an active transaction" }
        require(sourceRecordingId != targetRecordingId) { "Source and target recording are identical" }
        val source = requireRecording(sourceRecordingId)
        val target = requireRecording(targetRecordingId)
        requireMergeCompatible(source, target)
        val affectedSourceIds = (dao.sources(sourceRecordingId) + dao.sources(targetRecordingId))
            .mapNotNull { it.sourceId }
            .distinct()
        val operationStore = IdentityOperationStore(database)
        val before = operationStore.capture(listOf(sourceRecordingId, targetRecordingId))

        check(dao.update(mergeMetadata(source, target)) == 1) { "Target recording disappeared during merge" }
        dao.sources(sourceRecordingId).forEach { sourceRow ->
            check(dao.moveSourceToRecording(checkNotNull(sourceRow.sourceId), targetRecordingId) == 1) {
                "Source disappeared during merge"
            }
        }
        dao.identifiers(sourceRecordingId).forEach { dao.upsert(it.copy(recordingId = targetRecordingId)) }
        moveCredits(sourceRecordingId, targetRecordingId)
        moveVariants(sourceRecordingId, targetRecordingId)

        val targetBindings = dao.lyricBindings(targetRecordingId).associateBy { it.provider }
        dao.lyricBindings(sourceRecordingId).forEach { binding ->
            val existing = targetBindings[binding.provider]
            if (existing == null || binding.updatedAt > existing.updatedAt) {
                dao.upsert(binding.copy(recordingId = targetRecordingId))
            }
        }
        dao.deleteLyricBindings(sourceRecordingId)
        val targetCustomLyrics = dao.customLyricsForRecording(targetRecordingId).maxByOrNull { it.updatedAt }
        val sourceCustomLyrics = dao.customLyricsForRecording(sourceRecordingId).maxByOrNull { it.updatedAt }
        if (sourceCustomLyrics != null &&
            (targetCustomLyrics == null || sourceCustomLyrics.updatedAt > targetCustomLyrics.updatedAt)
        ) {
            dao.upsert(
                sourceCustomLyrics.copy(
                    identityKey = "recording:$targetRecordingId",
                    recordingId = targetRecordingId
                )
            )
        }
        dao.deleteCustomLyricsForRecording(sourceRecordingId)
        moveCanonicalBusinessReferences(sourceRecordingId, targetRecordingId)
        moveCandidates("RECORDING", sourceRecordingId, targetRecordingId)
        dao.deleteSelfOwnedPendingRecordingCandidates()
        dao.deleteJobs("RECORDING", sourceRecordingId)
        enqueueJob("RECORDING", targetRecordingId, "RECORDINGS_MERGED")
        RecordingRelationStore(database).rewriteAfterMerge(sourceRecordingId, targetRecordingId)
        dao.deleteSourceRecordingCandidates(
            recordingIds = listOf(sourceRecordingId, targetRecordingId),
            sourceIds = affectedSourceIds
        )
        if (affectedSourceIds.isNotEmpty()) {
            dao.invalidateSourceCandidateGeneration(affectedSourceIds)
        }
        check(dao.deleteRecording(sourceRecordingId) == 1) { "Source recording still owns dependent rows" }
        dao.deleteOrphanWorks()
        dao.refreshActiveSource(targetRecordingId)
        operationStore.recordReversible(
            IdentityOperationType.MERGE_RECORDINGS,
            sourceRecordingId,
            targetRecordingId,
            before
        )
        return requireRecording(targetRecordingId).toModel()
    }

    override fun splitSource(sourceId: Long): CanonicalRecording =
        splitSources(setOf(sourceId), RecordingSplitOptions())

    fun splitSources(
        sourceIds: Set<Long>,
        options: RecordingSplitOptions
    ): CanonicalRecording = database.runInTransaction(Callable {
        val selectedIds = sourceIds.filter { it > 0L }.distinct().sorted()
        require(selectedIds.isNotEmpty()) { "At least one source must be selected" }
        val selected = selectedIds.map { sourceId ->
            requireNotNull(dao.source(sourceId)) { "Unknown source $sourceId" }
        }
        val originalId = selected.map { it.recordingId }.distinct().singleOrNull()
            ?: throw IllegalArgumentException("Selected sources do not belong to one recording")
        val original = requireRecording(originalId)
        val allSources = dao.sources(originalId)
        require(selected.size < allSources.size) { "A split must leave at least one source in the original recording" }
        val selectedIdSet = selected.mapNotNullTo(linkedSetOf()) { it.sourceId }
        require(selectedIdSet.size == selectedIds.size) { "Selected source identity is incomplete" }
        val remaining = allSources.filterNot { it.sourceId in selectedIdSet }
        val preferredSelected = bestSource(selected)
        val now = System.currentTimeMillis()
        val operationStore = IdentityOperationStore(database)
        val before = operationStore.capture(listOf(originalId))
        val newUuid = UUID.randomUUID().toString()
        val newId = dao.insert(
            original.copy(
                id = null,
                canonicalUuid = newUuid,
                activeSourceId = null,
                musicBrainzRecordingId = "",
                title = preferredSelected.title.ifBlank { original.title },
                primaryArtistDisplay = preferredSelected.artist.ifBlank { original.primaryArtistDisplay },
                durationMs = preferredSelected.durationMs.takeIf { it > 0L } ?: original.durationMs,
                isrc = "",
                acoustId = "",
                matchStatus = IdentityMatchStatus.UNRESOLVED.name,
                confidence = 0.0,
                metadataSource = "USER_SPLIT",
                createdAt = now,
                updatedAt = now
            )
        )
        check(newId > 0L) { "Unable to create split recording" }

        selected.forEach { sourceRow ->
            check(dao.moveSourceToRecording(checkNotNull(sourceRow.sourceId), newId) == 1) {
                "Source disappeared during split"
            }
        }
        dao.credits(originalId).forEach { credit ->
            dao.upsert(credit.copy(recordingId = newId, confidence = 0.0))
        }
        val variantType = RecordingVariantRecognizer.recognize(preferredSelected.title, preferredSelected.album)
        dao.upsert(
            app.yukine.data.room.RecordingVariantEntity(
                variantGroupId = "user-split:$newUuid",
                recordingId = newId,
                variantType = variantType.name,
                displayName = preferredSelected.title,
                confidence = 0.7
            )
        )
        moveSplitCandidates(originalId, newId, selected)
        applySplitReferences(originalId, newId, selectedIds, selected, remaining, options)
        check(dao.update(metadataFromSources(original, remaining, now)) == 1) {
            "Original recording disappeared during split"
        }
        dao.deleteJobs("RECORDING", originalId)
        enqueueJob("RECORDING", originalId, "SOURCE_SPLIT_REEVALUATE")
        enqueueJob("RECORDING", newId, "SOURCE_SPLIT_REEVALUATE")
        dao.refreshActiveSource(originalId)
        dao.refreshActiveSource(newId)
        RecordingRelationStore(database).setManualCannotLink(
            firstRecordingId = originalId,
            secondRecordingId = newId,
            origin = "USER_SPLIT",
            evidenceJson = "{\"reason\":\"SOURCE_SPLIT\"}",
            now = now
        )
        operationStore.recordReversible(
            IdentityOperationType.SPLIT_RECORDING,
            originalId,
            newId,
            before
        )
        requireRecording(newId).toModel()
    })

    override fun pruneOrphans() {
        database.runInTransaction { OfflineMusicIdentityStore(dao).pruneMissingTracks() }
    }

    private fun requireRecording(recordingId: Long): CanonicalRecordingEntity =
        requireNotNull(dao.recording(recordingId)) { "Unknown recording $recordingId" }

    private fun requireMergeCompatible(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity
    ) {
        val sourceId = requireNotNull(source.id)
        val targetId = requireNotNull(target.id)
        val sourceCredits = dao.credits(sourceId)
        val targetCredits = dao.credits(targetId)
        RecordingRelationStore(database).relation(sourceId, targetId)?.let { relation ->
            require(
                relation.relationType != RecordingRelationship.CANNOT_LINK.name &&
                    relation.relationType != RecordingRelationship.SAME_WORK_DIFFERENT_VERSION.name
            ) {
                "Recording relation ${relation.relationType} prevents merge"
            }
        }
        RecordingMergePolicy.requireMergeAllowed(
            source = source,
            target = target,
            sourceCredits = sourceCredits,
            targetCredits = targetCredits,
            sourceArtistEvidence = loadPrimaryArtistEvidence(dao, sourceCredits),
            targetArtistEvidence = loadPrimaryArtistEvidence(dao, targetCredits),
            sourceVariants = dao.variants(sourceId),
            targetVariants = dao.variants(targetId)
        )
        requireCompatible("Work MBID", source.musicBrainzWorkId, target.musicBrainzWorkId)
    }

    private fun moveCredits(sourceId: Long, targetId: Long) {
        val sourceCredits = dao.credits(sourceId)
        val targetCredits = dao.credits(targetId)
        val sourceArtistEvidence = loadPrimaryArtistEvidence(dao, sourceCredits).associateBy { it.artistId }
        val targetArtistEvidence = loadPrimaryArtistEvidence(dao, targetCredits)
        val targetCreditsByKey = targetCredits
            .associateByTo(linkedMapOf()) { Triple(it.artistId, it.role, it.position) }
        sourceCredits.forEach { sourceCredit ->
            val mergedArtistId = sourceArtistEvidence[sourceCredit.artistId]
                ?.takeIf { sourceCredit.role == "PRIMARY" || sourceCredit.role == "UNKNOWN" }
                ?.let { sourceArtist ->
                    targetArtistEvidence.firstOrNull { targetArtist ->
                        RecordingMergePolicy.compatiblePrimaryArtist(sourceArtist, targetArtist)
                    }?.artistId
                }
                ?: sourceCredit.artistId
            val key = Triple(mergedArtistId, sourceCredit.role, sourceCredit.position)
            val existing = targetCreditsByKey[key]
            val mergedCredit = if (existing == null) {
                sourceCredit.copy(recordingId = targetId, artistId = mergedArtistId)
            } else {
                existing.copy(
                    creditedName = existing.creditedName.ifBlank { sourceCredit.creditedName },
                    joinPhrase = existing.joinPhrase.ifBlank { sourceCredit.joinPhrase },
                    confidence = max(existing.confidence, sourceCredit.confidence)
                )
            }
            dao.upsert(mergedCredit)
            targetCreditsByKey[key] = mergedCredit
        }
        dao.deleteCredits(sourceId)
    }

    private fun moveVariants(sourceId: Long, targetId: Long) {
        val targetVariants = dao.variants(targetId).associateBy { it.variantGroupId }
        dao.variants(sourceId).forEach { sourceVariant ->
            val existing = targetVariants[sourceVariant.variantGroupId]
            dao.upsert(
                if (existing == null) {
                    sourceVariant.copy(recordingId = targetId)
                } else {
                    existing.copy(
                        displayName = existing.displayName.ifBlank { sourceVariant.displayName },
                        confidence = max(existing.confidence, sourceVariant.confidence)
                    )
                }
            )
        }
        dao.deleteVariants(sourceId)
    }

    private fun moveCanonicalBusinessReferences(sourceId: Long, targetId: Long) {
        val libraryDao = database.libraryDao()
        val sourceFavorite = libraryDao.recordingFavorite(sourceId)
        val targetFavorite = libraryDao.recordingFavorite(targetId)
        if (sourceFavorite != null) {
            libraryDao.putRecordingFavorite(
                app.yukine.data.room.RecordingFavoriteEntity(
                    targetId,
                    minOf(sourceFavorite.createdAt, targetFavorite?.createdAt ?: sourceFavorite.createdAt),
                    mergedFavoriteSyncState(sourceFavorite.syncState, targetFavorite?.syncState)
                )
            )
            libraryDao.deleteRecordingFavorite(sourceId)
        }

        val historyDao = database.historyDao()
        val sourceHistory = historyDao.recordingHistory(sourceId)
        val targetHistory = historyDao.recordingHistory(targetId)
        if (sourceHistory != null) {
            val newest = listOfNotNull(sourceHistory, targetHistory).maxBy { it.playedAt }
            historyDao.upsertRecordingHistory(
                app.yukine.data.room.RecordingPlayHistoryEntity(
                    targetId,
                    newest.representativeTrackId,
                    maxOf(sourceHistory.playedAt, targetHistory?.playedAt ?: 0L),
                    sourceHistory.playCount + (targetHistory?.playCount ?: 0)
                )
            )
            historyDao.deleteRecordingHistory(sourceId)
        }
        historyDao.moveRecordingEvents(sourceId, targetId)
        database.playbackPersistenceDao().moveQueueRecording(sourceId, targetId)

        val playlistDao = database.playlistDao()
        playlistDao.playlistRecordingReferences(sourceId).forEach { sourceItem ->
            val targetItem = playlistDao.playlistRecordingItem(sourceItem.playlistId, targetId)
            if (targetItem == null) {
                playlistDao.upsertPlaylistRecordingItem(sourceItem.copy(recordingId = targetId))
            } else {
                playlistDao.upsertPlaylistRecordingItem(
                    targetItem.copy(
                        sortKey = minOf(sourceItem.sortKey, targetItem.sortKey),
                        addedAt = minOf(sourceItem.addedAt, targetItem.addedAt)
                    )
                )
            }
            playlistDao.removePlaylistRecordingItem(sourceItem.playlistId, sourceId)
        }
    }

    private fun mergedFavoriteSyncState(source: String, target: String?): String {
        val priority = listOf("NEEDS_CONFIRMATION", "RETRY", "PENDING", "LOCAL_ONLY", "SYNCED")
        return listOfNotNull(source, target).minByOrNull { value ->
            priority.indexOf(value).takeIf { it >= 0 } ?: priority.size
        } ?: "LOCAL_ONLY"
    }

    private fun moveCandidates(targetType: String, sourceId: Long, targetId: Long) {
        dao.candidates(targetType, sourceId).forEach { candidate ->
            val existing = dao.candidate(
                targetType,
                targetId,
                candidate.provider,
                candidate.providerItemId
            )
            if (existing == null) {
                dao.upsert(candidate.copy(targetId = targetId))
            } else {
                if (candidatePreference(candidate) > candidatePreference(existing)) {
                    dao.upsert(
                        candidate.copy(
                            candidateId = existing.candidateId,
                            targetId = targetId,
                            createdAt = minOf(candidate.createdAt, existing.createdAt),
                            updatedAt = maxOf(candidate.updatedAt, existing.updatedAt)
                        )
                    )
                }
                dao.deleteCandidate(candidate.candidateId)
            }
        }
    }

    private fun moveSplitCandidates(
        originalId: Long,
        newId: Long,
        selected: List<app.yukine.data.room.TrackSourceMappingEntity>
    ) {
        val selectedKeys = selected.mapTo(hashSetOf()) {
            normalizeProvider(it.provider) to it.providerTrackId.trim()
        }
        dao.candidates("RECORDING", originalId)
            .filter { normalizeProvider(it.provider) to it.providerItemId.trim() in selectedKeys }
            .forEach { dao.upsert(it.copy(targetId = newId, updatedAt = System.currentTimeMillis())) }
    }

    private fun applySplitReferences(
        originalId: Long,
        newId: Long,
        selectedIds: List<Long>,
        selected: List<app.yukine.data.room.TrackSourceMappingEntity>,
        remaining: List<app.yukine.data.room.TrackSourceMappingEntity>,
        options: RecordingSplitOptions
    ) {
        val libraryDao = database.libraryDao()
        if (options.favoriteDestination == RecordingSplitDestination.NEW_RECORDING) {
            libraryDao.recordingFavorite(originalId)?.let { favorite ->
                libraryDao.putRecordingFavorite(favorite.copy(recordingId = newId))
                libraryDao.deleteRecordingFavorite(originalId)
            }
        }

        val playlistDao = database.playlistDao()
        val selectedTrackIds = selected.mapNotNullTo(hashSetOf()) { it.localTrackId }
        val selectedTrackId = selected.firstNotNullOfOrNull { it.localTrackId }
        val remainingTrackId = remaining.firstNotNullOfOrNull { it.localTrackId }
        playlistDao.playlistRecordingReferences(originalId).forEach { item ->
            if (options.playlistDestination == RecordingSplitDestination.NEW_RECORDING) {
                playlistDao.upsertPlaylistRecordingItem(
                    item.copy(
                        recordingId = newId,
                        representativeTrackId = selectedTrackId ?: item.representativeTrackId
                    )
                )
                playlistDao.removePlaylistRecordingItem(item.playlistId, originalId)
            } else if (remainingTrackId != null && item.representativeTrackId in selectedTrackIds) {
                playlistDao.upsertPlaylistRecordingItem(item.copy(representativeTrackId = remainingTrackId))
            }
        }

        val queueDao = database.playbackPersistenceDao()
        if (options.queueDestination == RecordingSplitDestination.NEW_RECORDING) {
            queueDao.moveQueueRecording(originalId, newId)
            queueDao.clearQueuePreferredSourcesExcept(newId, selectedIds)
        } else {
            queueDao.clearQueuePreferredSources(originalId, selectedIds)
        }
        database.historyDao().clearRecordingEventSources(originalId, selectedIds)
    }

    private fun metadataFromSources(
        recording: CanonicalRecordingEntity,
        sources: List<app.yukine.data.room.TrackSourceMappingEntity>,
        updatedAt: Long
    ): CanonicalRecordingEntity {
        val preferred = bestSource(sources)
        return recording.copy(
            title = preferred.title.ifBlank { recording.title },
            primaryArtistDisplay = preferred.artist.ifBlank { recording.primaryArtistDisplay },
            durationMs = preferred.durationMs.takeIf { it > 0L } ?: recording.durationMs,
            updatedAt = maxOf(recording.updatedAt, updatedAt)
        )
    }

    private fun bestSource(
        sources: List<app.yukine.data.room.TrackSourceMappingEntity>
    ): app.yukine.data.room.TrackSourceMappingEntity = sources.maxWithOrNull(
        compareBy<app.yukine.data.room.TrackSourceMappingEntity> { if (it.playable) 1 else 0 }
            .thenBy { if (it.matchStatus == IdentityMatchStatus.CONFIRMED.name) 1 else 0 }
            .thenBy { sourcePriority(it.provider) }
            .thenBy { it.qualityScore }
            .thenBy { it.lastSuccessfulAt }
    ) ?: throw IllegalArgumentException("Recording has no source")

    private fun enqueueJob(targetType: String, targetId: Long, reason: String) {
        val now = System.currentTimeMillis()
        dao.insertJob(
            IdentityResolutionJobEntity(
                jobId = UUID.randomUUID().toString(),
                targetType = targetType,
                targetId = targetId,
                priority = 50,
                reason = reason,
                attemptCount = 0,
                nextAttemptAt = 0L,
                lastError = "",
                status = "PENDING",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun withIdentifier(
        recording: CanonicalRecordingEntity,
        type: String,
        value: String
    ): CanonicalRecordingEntity = when (type) {
        "MUSICBRAINZ_RECORDING_ID", "MBID" -> recording.copy(
            musicBrainzRecordingId = compatibleValue(
                "Recording MBID",
                recording.musicBrainzRecordingId,
                value
            )
        )
        "MUSICBRAINZ_WORK_ID", "WORK_MBID" -> recording.copy(
            musicBrainzWorkId = compatibleValue("Work MBID", recording.musicBrainzWorkId, value)
        )
        "ISRC" -> recording.copy(isrc = compatibleValue("ISRC", recording.isrc, value))
        "ACOUSTID", "ACOUST_ID" -> recording.copy(
            acoustId = compatibleValue("AcoustID", recording.acoustId, value)
        )
        else -> recording
    }

    private fun mergeMetadata(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity
    ): CanonicalRecordingEntity {
        val preferred = if (source.confidence > target.confidence) source else target
        val fallback = if (preferred === source) target else source
        return target.copy(
            workId = mergedWorkId(source, target),
            musicBrainzRecordingId = target.musicBrainzRecordingId.ifBlank { source.musicBrainzRecordingId },
            musicBrainzWorkId = target.musicBrainzWorkId.ifBlank { source.musicBrainzWorkId },
            title = preferred.title.ifBlank { fallback.title },
            primaryArtistDisplay = preferred.primaryArtistDisplay.ifBlank { fallback.primaryArtistDisplay },
            durationMs = preferred.durationMs.takeIf { it > 0L } ?: fallback.durationMs,
            isrc = target.isrc.ifBlank { source.isrc },
            acoustId = target.acoustId.ifBlank { source.acoustId },
            confidence = max(target.confidence, source.confidence),
            metadataSource = preferred.metadataSource.ifBlank { fallback.metadataSource },
            updatedAt = maxOf(System.currentTimeMillis(), target.updatedAt, source.updatedAt)
        )
    }

    private fun mergedWorkId(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity
    ): Long? = when {
        source.musicBrainzWorkId.isNotBlank() && target.musicBrainzWorkId.isBlank() ->
            source.workId ?: target.workId
        else -> target.workId ?: source.workId
    }

    private fun differentStrongWork(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity
    ): Boolean = source.musicBrainzWorkId.isNotBlank() &&
        target.musicBrainzWorkId.isNotBlank() &&
        !source.musicBrainzWorkId.equals(target.musicBrainzWorkId, ignoreCase = true)

    private fun compatibleValue(label: String, current: String, incoming: String): String {
        requireCompatible(label, current, incoming)
        return current.ifBlank { incoming }
    }

    private fun requireCompatible(label: String, first: String, second: String) {
        require(first.isBlank() || second.isBlank() || first.equals(second, ignoreCase = true)) {
            "$label conflict"
        }
    }

    private fun candidatePreference(candidate: IdentityCandidateEntity): Double {
        val statusBoost = when (candidate.status) {
            IdentityCandidateStatus.USER_CONFIRMED.name -> 3.0
            IdentityCandidateStatus.AUTO_CONFIRMED.name -> 2.0
            IdentityCandidateStatus.PENDING.name -> 1.0
            else -> 0.0
        }
        return statusBoost + candidate.score.coerceIn(0.0, 1.0)
    }

    private fun normalizeIdentifier(type: String, value: String): String = when (type) {
        "ISRC" -> value.filter(Char::isLetterOrDigit).uppercase(Locale.ROOT)
        "MUSICBRAINZ_RECORDING_ID", "MUSICBRAINZ_WORK_ID", "MBID", "WORK_MBID" ->
            value.trim().lowercase(Locale.ROOT)
        else -> value.trim()
    }

    private fun normalizeProvider(provider: String): String = provider.trim().lowercase(Locale.ROOT)

    private fun sourcePriority(provider: String): Int = when (provider.lowercase(Locale.ROOT)) {
        "local" -> 600
        "webdav" -> 500
        "document" -> 450
        "stream" -> 400
        "netease" -> 300
        "qqmusic" -> 250
        "luoxue" -> 200
        else -> 100
    }
}
