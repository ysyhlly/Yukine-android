package app.yukine.data

import app.yukine.data.room.ArtistAliasEntity
import app.yukine.data.room.ArtistCreditParser
import app.yukine.data.room.CanonicalArtistEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.MusicIdentityDao
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.ArtistAlias
import app.yukine.identity.ArtistCredit
import app.yukine.identity.ArtistIdentityRepository
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityMatchStatus
import app.yukine.model.Track
import java.util.UUID
import java.util.concurrent.Callable
import kotlin.math.max

class RoomArtistIdentityRepository(
    private val database: YukineDatabase
) : ArtistIdentityRepository {
    private val dao: MusicIdentityDao
        get() = database.musicIdentityDao()

    override fun ensureArtistsForTrack(track: Track): List<ArtistCredit> {
        val recording = RoomRecordingIdentityRepository(database).ensureCanonicalForTrack(track)
        return creditsForRecording(recording.recordingId)
    }

    override fun artistByKey(artistKey: Long): CanonicalArtist? = dao.artist(artistKey)?.toModel()

    override fun artistById(artistId: String): CanonicalArtist? =
        dao.canonicalArtist(artistId.trim())?.toModel()

    override fun artistForProvider(provider: String, providerArtistId: String): CanonicalArtist? =
        dao.artistForProvider(provider.trim().lowercase(), providerArtistId.trim())?.toModel()

    override fun aliases(artistKey: Long): List<ArtistAlias> {
        val artist = dao.artist(artistKey) ?: return emptyList()
        return dao.aliases(artistKey).map { it.toModel(artist) }
    }

    override fun creditsForRecording(recordingId: Long): List<ArtistCredit> {
        val recording = dao.recording(recordingId) ?: return emptyList()
        return dao.credits(recordingId).mapNotNull { credit ->
            dao.artist(credit.artistId)?.let { credit.toModel(recording, it) }
        }
    }

    override fun confirmAlias(artistKey: Long, alias: ArtistAlias) {
        database.runInTransaction {
            val artist = requireArtist(artistKey)
            require(alias.artistKey == artistKey) { "Alias artist key mismatch" }
            require(alias.artistId == artist.artistUuid) { "Alias artist UUID mismatch" }
            val normalized = alias.normalizedAlias.ifBlank {
                ArtistCreditParser.normalizeAlias(alias.alias)
            }
            require(normalized.isNotBlank()) { "Alias cannot be blank" }
            dao.upsert(
                ArtistAliasEntity(
                    artistId = artistKey,
                    alias = alias.alias.trim(),
                    normalizedAlias = normalized,
                    locale = alias.locale.trim(),
                    script = alias.script.trim(),
                    aliasType = alias.aliasType.name,
                    source = alias.source.ifBlank { "USER_CONFIRMED" },
                    confidence = max(0.92, alias.confidence.coerceIn(0.0, 1.0)),
                    verifiedAt = alias.verifiedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        }
    }

    override fun mergeArtists(
        sourceArtistKey: Long,
        targetArtistKey: Long
    ): CanonicalArtist = database.runInTransaction(Callable {
        require(sourceArtistKey != targetArtistKey) { "Source and target artist are identical" }
        val source = requireArtist(sourceArtistKey)
        val target = requireArtist(targetArtistKey)
        requireCompatibleMbid(source, target)
        dao.update(
            target.copy(
                displayName = target.displayName.ifBlank { source.displayName },
                sortName = target.sortName.ifBlank { source.sortName },
                artistType = target.artistType.takeUnless { it == "UNKNOWN" } ?: source.artistType,
                countryCode = target.countryCode.ifBlank { source.countryCode },
                musicBrainzArtistId = target.musicBrainzArtistId.ifBlank {
                    source.musicBrainzArtistId
                },
                confidence = max(target.confidence, source.confidence),
                updatedAt = maxOf(System.currentTimeMillis(), target.updatedAt, source.updatedAt)
            )
        )

        val targetAliases = dao.aliases(targetArtistKey)
            .associateBy { it.normalizedAlias to it.locale }
        dao.aliases(sourceArtistKey).forEach { alias ->
            val existing = targetAliases[alias.normalizedAlias to alias.locale]
            if (existing == null || alias.confidence > existing.confidence) {
                dao.upsert(alias.copy(artistId = targetArtistKey))
            }
        }
        dao.deleteAliases(sourceArtistKey)
        dao.artistMappings(sourceArtistKey).forEach {
            dao.upsert(it.copy(artistId = targetArtistKey))
        }
        moveCredits(sourceArtistKey, targetArtistKey)
        moveCandidates("ARTIST", sourceArtistKey, targetArtistKey)
        dao.deleteJobs("ARTIST", sourceArtistKey)
        enqueueJob(targetArtistKey, "ARTISTS_MERGED")
        check(dao.deleteArtist(sourceArtistKey) == 1) { "Source artist still owns dependent rows" }
        requireArtist(targetArtistKey).toModel()
    })

    override fun splitArtistMapping(mappingId: Long): CanonicalArtist =
        database.runInTransaction(Callable {
            val mapping = requireNotNull(dao.artistMapping(mappingId)) { "Unknown artist mapping $mappingId" }
            val source = requireArtist(mapping.artistId)
            val now = System.currentTimeMillis()
            val displayName = mapping.displayName.ifBlank { source.displayName }
            val artistUuid = UUID.randomUUID().toString()
            val newArtistKey = dao.insert(
                source.copy(
                    id = null,
                    artistUuid = artistUuid,
                    displayName = displayName,
                    sortName = ArtistCreditParser.normalizeAlias(displayName),
                    musicBrainzArtistId = if (mapping.provider == "musicbrainz") {
                        mapping.providerArtistId
                    } else {
                        ""
                    },
                    matchStatus = IdentityMatchStatus.UNRESOLVED.name,
                    confidence = 0.0,
                    metadataSource = "USER_SPLIT",
                    createdAt = now,
                    updatedAt = now
                )
            )
            check(newArtistKey > 0L) { "Unable to create split artist" }
            dao.upsert(mapping.copy(artistId = newArtistKey, status = "CONFIRMED", confidence = 1.0))
            dao.upsert(
                ArtistAliasEntity(
                    artistId = newArtistKey,
                    alias = displayName,
                    normalizedAlias = ArtistCreditParser.normalizeAlias(displayName),
                    locale = "",
                    script = "",
                    aliasType = "PRIMARY",
                    source = "USER_SPLIT",
                    confidence = 1.0,
                    verifiedAt = now
                )
            )
            enqueueJob(newArtistKey, "ARTIST_MAPPING_SPLIT")
            requireArtist(newArtistKey).toModel()
        })

    private fun moveCredits(sourceArtistKey: Long, targetArtistKey: Long) {
        dao.creditsForArtist(sourceArtistKey).forEach { sourceCredit ->
            val existing = dao.credits(sourceCredit.recordingId).firstOrNull {
                it.artistId == targetArtistKey &&
                    it.role == sourceCredit.role &&
                    it.position == sourceCredit.position
            }
            if (existing == null || sourceCredit.confidence > existing.confidence) {
                dao.upsert(sourceCredit.copy(artistId = targetArtistKey))
            }
            dao.deleteCredit(
                sourceCredit.recordingId,
                sourceArtistKey,
                sourceCredit.role,
                sourceCredit.position
            )
        }
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

    private fun enqueueJob(artistKey: Long, reason: String) {
        val now = System.currentTimeMillis()
        dao.insertJob(
            IdentityResolutionJobEntity(
                jobId = UUID.randomUUID().toString(),
                targetType = "ARTIST",
                targetId = artistKey,
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

    private fun requireArtist(artistKey: Long): CanonicalArtistEntity =
        requireNotNull(dao.artist(artistKey)) { "Unknown artist $artistKey" }

    private fun requireCompatibleMbid(source: CanonicalArtistEntity, target: CanonicalArtistEntity) {
        require(
            source.musicBrainzArtistId.isBlank() ||
                target.musicBrainzArtistId.isBlank() ||
                source.musicBrainzArtistId.equals(target.musicBrainzArtistId, ignoreCase = true)
        ) { "Artist MBID conflict" }
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
}
