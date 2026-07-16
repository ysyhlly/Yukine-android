package app.yukine.data.enrichment

import app.yukine.data.room.YukineDatabase
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.ArtistAlias
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalRecording
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
    private val client: MetadataGatewayClient
) : AnonymousArtistMetadataProvider {
    override val providerName: String = MetadataGatewayClient.PROVIDER

    override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>): AnonymousArtistProviderResult =
        client.searchArtist(artist, aliases.map(ArtistAlias::alias))
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
