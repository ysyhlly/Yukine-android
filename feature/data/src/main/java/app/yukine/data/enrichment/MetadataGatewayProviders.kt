package app.yukine.data.enrichment

import app.yukine.data.room.YukineDatabase
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.AnonymousAlbumMetadataProvider
import app.yukine.identity.AnonymousAlbumProviderResult
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.ArtistAlias
import app.yukine.identity.AlbumAlias
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalAlbum
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityTextNormalizer
import org.json.JSONObject

class MetadataGatewayRecordingProvider(
    private val client: MetadataGatewayClient,
    private val fingerprints: RecordingFingerprintLookup
) : AnonymousRecordingMetadataProvider {
    override val providerName: String = MetadataGatewayClient.PROVIDER

    override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult =
        client.searchRecording(recording, primaryArtist, fingerprints.forRecording(recording.recordingId))
}

class MetadataGatewayArtistProvider(
    private val client: MetadataGatewayClient,
    private val avatarLookup: ArtistAvatarLookup = ArtistAvatarLookup { "" }
) : AnonymousArtistMetadataProvider {
    override val providerName: String = MetadataGatewayClient.PROVIDER

    override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>): AnonymousArtistProviderResult {
        val result = client.searchArtist(artist, aliases.map(ArtistAlias::alias))
        if (result.candidates.any { it.avatarUrl.isNotBlank() }) return result
        val targetMbid = artist.musicBrainzArtistId.trim()
        val preferred = if (targetMbid.isNotBlank()) {
            result.candidates.singleOrNull { it.artistMbid.equals(targetMbid, ignoreCase = true) }
        } else {
            val normalizedName = IdentityTextNormalizer.normalizeForSearch(artist.displayName)
            result.candidates.filter { candidate ->
                candidate.artistMbid.isNotBlank() &&
                    candidate.providerScore >= 0.95 &&
                    IdentityTextNormalizer.normalizeForSearch(candidate.displayName) == normalizedName
            }.singleOrNull()
        } ?: return result
        val avatarUrl = runCatching { avatarLookup.forArtistMbid(preferred.artistMbid) }
            .getOrDefault("")
            .trim()
            .takeIf { it.startsWith("https://", ignoreCase = true) }
            .orEmpty()
        if (avatarUrl.isBlank()) return result
        return result.copy(
            candidates = result.candidates.map { candidate ->
                if (candidate === preferred) candidate.copy(avatarUrl = avatarUrl) else candidate
            }
        )
    }
}

class MetadataGatewayAlbumProvider(
    private val client: MetadataGatewayClient
) : AnonymousAlbumMetadataProvider {
    override val providerName: String = MetadataGatewayClient.PROVIDER

    override fun search(album: CanonicalAlbum, aliases: List<AlbumAlias>): AnonymousAlbumProviderResult =
        client.searchAlbum(album, aliases.map(AlbumAlias::alias))
}

fun interface ArtistAvatarLookup {
    fun forArtistMbid(artistMbid: String): String
}

fun interface RecordingFingerprintLookup {
    fun forRecording(recordingId: Long): GatewayAudioFingerprint?
}

/** Reads cold fingerprint evidence only when the background metadata worker asks for it. */
class RoomRecordingFingerprintLookup(database: YukineDatabase) : RecordingFingerprintLookup {
    private val dao = database.musicIdentityDao()

    override fun forRecording(recordingId: Long): GatewayAudioFingerprint? {
        val source = dao.sources(recordingId)
            .asSequence()
            .filter { it.matchStatus == "CONFIRMED" && it.playable }
            .sortedBy { it.sourceId ?: Long.MAX_VALUE }
            .firstOrNull { source ->
                source.sourceId?.let(dao::audioFeature)?.chromaprint?.isNotBlank() == true
            } ?: return null
        val entity = source.sourceId?.let(dao::audioFeature) ?: return null
        return runCatching {
            val segments = JSONObject(entity.chromaprint).optJSONArray("segments") ?: return@runCatching null
            val head = (0 until segments.length())
                .mapNotNull(segments::optJSONObject)
                .minByOrNull { it.optLong("startMs", Long.MAX_VALUE) }
                ?: return@runCatching null
            val fingerprint = head.optString("fingerprint").trim()
            if (fingerprint.isBlank()) return@runCatching null
            GatewayAudioFingerprint(
                fingerprint = fingerprint,
                durationSeconds = ((source.durationMs.coerceAtLeast(head.optLong("durationMs"))) / 1_000L)
                    .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            )
        }.getOrNull()
    }
}
