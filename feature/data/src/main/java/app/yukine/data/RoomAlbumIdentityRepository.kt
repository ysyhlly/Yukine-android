package app.yukine.data

import app.yukine.data.room.AlbumAliasEntity
import app.yukine.data.room.AlbumSourceMappingEntity
import app.yukine.data.room.CanonicalAlbumEntity
import app.yukine.data.room.MusicIdentityDao
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.AlbumAlias
import app.yukine.identity.AlbumIdentityRepository
import app.yukine.identity.AnonymousAlbumCandidate
import app.yukine.identity.CanonicalAlbum
import app.yukine.identity.IdentityMatchStatus
import app.yukine.identity.IdentityTextNormalizer

class RoomAlbumIdentityRepository(
    private val database: YukineDatabase
) : AlbumIdentityRepository {
    private val dao: MusicIdentityDao
        get() = database.musicIdentityDao()

    override fun albumByKey(albumKey: Long): CanonicalAlbum? =
        dao.album(albumKey)?.toModel(albumArtistDisplay(dao.album(albumKey)))

    override fun albumForProvider(provider: String, providerAlbumId: String): CanonicalAlbum? {
        val entity = dao.albumForProvider(provider.trim().lowercase(), providerAlbumId.trim()) ?: return null
        return entity.toModel(albumArtistDisplay(entity))
    }

    override fun aliases(albumKey: Long): List<AlbumAlias> =
        dao.albumAliases(albumKey).map(AlbumAliasEntity::toModel)

    override fun confirmCandidate(
        albumKey: Long,
        candidate: AnonymousAlbumCandidate,
        verifiedAt: Long
    ) {
        database.runInTransaction {
            val album = requireNotNull(dao.album(albumKey)) { "Album $albumKey no longer exists" }
            requireCompatibleId(
                "release-group",
                album.musicBrainzReleaseGroupId,
                candidate.musicBrainzReleaseGroupId
            )
            requireCompatibleId("release", album.musicBrainzReleaseId, candidate.musicBrainzReleaseId)
            val time = maxOf(verifiedAt, album.updatedAt)
            val confidence = candidate.providerScore.coerceIn(0.92, 1.0)
            val preferVerifiedName =
                album.matchStatus != IdentityMatchStatus.CONFIRMED.name ||
                    album.metadataSource in setOf("", "LOCAL", "MIGRATION_V31")
            dao.update(
                album.copy(
                    displayName = if (preferVerifiedName) candidate.title.trim() else album.displayName,
                    sortName = if (preferVerifiedName) normalized(candidate.title) else album.sortName,
                    musicBrainzReleaseGroupId = album.musicBrainzReleaseGroupId.ifBlank {
                        candidate.musicBrainzReleaseGroupId.trim()
                    },
                    musicBrainzReleaseId = album.musicBrainzReleaseId.ifBlank {
                        candidate.musicBrainzReleaseId.trim()
                    },
                    releaseType = album.releaseType.ifBlank { candidate.releaseType.trim() },
                    year = album.year.takeIf { it > 0 } ?: candidate.year.coerceAtLeast(0),
                    matchStatus = IdentityMatchStatus.CONFIRMED.name,
                    confidence = maxOf(album.confidence, confidence),
                    metadataSource = candidate.provider.trim().ifBlank { album.metadataSource },
                    updatedAt = time
                )
            )
            (candidate.aliases + candidate.title + album.displayName)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinctBy(::normalized)
                .forEach { alias ->
                    dao.upsert(
                        AlbumAliasEntity(
                            albumId = albumKey,
                            alias = alias,
                            normalizedAlias = normalized(alias),
                            locale = "",
                            aliasType = if (alias == candidate.title.trim()) "PRIMARY" else "ALIAS",
                            source = candidate.provider,
                            confidence = confidence,
                            verifiedAt = time
                        )
                    )
                }
            dao.upsert(
                AlbumSourceMappingEntity(
                    mappingId = dao.albumMappings(albumKey).firstOrNull {
                        it.provider.equals(candidate.provider, ignoreCase = true) &&
                            it.providerAlbumId == candidate.providerAlbumId
                    }?.mappingId,
                    albumId = albumKey,
                    provider = candidate.provider.trim().lowercase(),
                    providerAlbumId = candidate.providerAlbumId.trim(),
                    displayName = candidate.title.trim(),
                    musicBrainzReleaseGroupId = candidate.musicBrainzReleaseGroupId.trim(),
                    musicBrainzReleaseId = candidate.musicBrainzReleaseId.trim(),
                    status = IdentityMatchStatus.CONFIRMED.name,
                    confidence = confidence,
                    lastVerifiedAt = time
                )
            )
        }
    }

    private fun albumArtistDisplay(album: CanonicalAlbumEntity?): String =
        album?.albumArtistId?.let(dao::artist)?.displayName.orEmpty()

    private fun normalized(value: String): String =
        IdentityTextNormalizer.normalizeForSearch(value)

    private fun requireCompatibleId(label: String, current: String, candidate: String) {
        require(
            current.isBlank() ||
                candidate.isBlank() ||
                current.equals(candidate, ignoreCase = true)
        ) { "Conflicting MusicBrainz $label ID" }
    }
}
