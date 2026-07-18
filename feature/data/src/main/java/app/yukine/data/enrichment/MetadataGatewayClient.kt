package app.yukine.data.enrichment

import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.ArtistType
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderResponseCacheRepository
import app.yukine.identity.RecordingVariantRecognizer
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONObject

data class GatewayAudioFingerprint(
    val fingerprint: String,
    val durationSeconds: Int
)

data class GatewayLyrics(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val syncedLyrics: String,
    val plainLyrics: String
)

data class GatewayLyricsSearchResult(
    val lyrics: GatewayLyrics?,
    val allEndpointsFailed: Boolean = false
)

/** Client for the normalized ECHO metadata gateway contract. */
class MetadataGatewayClient(
    private val cache: ProviderResponseCacheRepository,
    private val transport: MetadataHttpTransport,
    endpoint: String,
    applicationVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = 30L * 24L * 60L * 60L * 1_000L
) {
    val endpoint: String = normalizeEndpoint(endpoint)
    private val userAgent = "EchoAndroid/${applicationVersion.trim().ifBlank { "unknown" }}"

    fun searchRecording(
        recording: CanonicalRecording,
        primaryArtist: String,
        fingerprint: GatewayAudioFingerprint?
    ): AnonymousProviderResult {
        if (endpoint.isBlank()) return AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
        val params = linkedMapOf(
            "title" to recording.title,
            "artist" to primaryArtist,
            "durationMs" to recording.durationMs.takeIf { it > 0L }?.toString().orEmpty(),
            "recordingMbid" to recording.musicBrainzRecordingId,
            "isrc" to recording.isrc,
            "fingerprint" to fingerprint?.fingerprint.orEmpty(),
            "fingerprintDuration" to fingerprint?.durationSeconds?.toString().orEmpty(),
            "limit" to "12"
        )
        return fetch("v1/recordings/search", params, ::parseRecordings)
            ?: AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
    }

    fun searchArtist(artist: CanonicalArtist, aliases: List<String>): AnonymousArtistProviderResult {
        if (endpoint.isBlank()) return AnonymousArtistProviderResult(emptyList(), allEndpointsFailed = true)
        val params = linkedMapOf(
            "name" to artist.displayName,
            "aliases" to aliases.joinToString("\u001f"),
            "artistMbid" to artist.musicBrainzArtistId,
            "limit" to "10",
            "responseVersion" to "artist-profile-v2"
        )
        return fetch("v1/artists/search", params, ::parseArtists)
            ?: AnonymousArtistProviderResult(emptyList(), allEndpointsFailed = true)
    }

    fun searchLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): GatewayLyricsSearchResult {
        if (endpoint.isBlank()) return GatewayLyricsSearchResult(null, allEndpointsFailed = true)
        val params = linkedMapOf(
            "title" to title,
            "artist" to artist,
            "album" to album,
            "durationMs" to durationMs.takeIf { it > 0L }?.toString().orEmpty()
        )
        return fetch(
            "v1/lyrics/search",
            params,
            ::parseLyrics,
            LYRICS_CACHE_TTL_MS
        ) ?: GatewayLyricsSearchResult(null, allEndpointsFailed = true)
    }

    private fun <T> fetch(
        path: String,
        params: Map<String, String>,
        parser: (String, Boolean) -> T?,
        ttlMs: Long = cacheTtlMs
    ): T? {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val requestPath = "$path?$query"
        val requestHash = sha256(requestPath)
        val cached = cache.response(PROVIDER, endpoint, requestHash, now())
        if (cached?.freshness == ProviderCacheFreshness.FRESH) {
            parser(cached.responseJson, true)?.let { return it }
        }
        if (!cache.endpointHealth(PROVIDER, endpoint).canRequest(now())) {
            return cached?.responseJson?.let { parser(it, true) }
        }
        val response = runCatching {
            transport.get(
                endpoint + requestPath,
                mapOf("Accept" to "application/json", "User-Agent" to userAgent)
            )
        }
        val value = response.getOrNull()
        if (value != null && value.statusCode in 200..299 && value.body.isNotBlank()) {
            parser(value.body, false)?.let {
                cache.saveSuccess(PROVIDER, endpoint, requestHash, value.body, now(), ttlMs)
                return it
            }
        }
        cache.recordFailure(
            PROVIDER,
            endpoint,
            response.exceptionOrNull()?.message ?: "HTTP ${value?.statusCode ?: 0}",
            now()
        )
        return cached?.responseJson?.let { parser(it, true) }
    }

    private fun parseRecordings(body: String, fromCache: Boolean): AnonymousProviderResult? = runCatching {
        val root = JSONObject(body)
        val values = root.optJSONArray("recordings") ?: return@runCatching AnonymousProviderResult(emptyList())
        val candidates = buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                val artists = buildList {
                    val array = item.optJSONArray("artists")
                    if (array != null) for (artistIndex in 0 until array.length()) {
                        val artist = array.optJSONObject(artistIndex) ?: continue
                        add(ProviderArtistCandidate(
                            artist.optString("id"),
                            artist.optString("name"),
                            artist.optString("sortName")
                        ))
                    }
                }
                add(AnonymousRecordingCandidate(
                    provider = item.optString("provider", "musicbrainz"),
                    providerItemId = id,
                    title = title,
                    artists = artists,
                    album = item.optString("album"),
                    durationMs = item.optLong("durationMs", 0L),
                    isrc = item.optString("isrc"),
                    recordingMbid = item.optString("recordingMbid"),
                    workMbid = item.optString("workMbid"),
                    acoustId = item.optString("acoustId"),
                    coverUrl = trustedCoverUrl(item.optString("coverUrl")),
                    fingerprintVerified = item.optBoolean("fingerprintVerified", false),
                    variantType = RecordingVariantRecognizer.recognize(title, item.optString("album")),
                    providerScore = item.optDouble("score", 0.0).coerceIn(0.0, 1.0)
                ))
            }
        }
        AnonymousProviderResult(candidates, endpoint, fromCache = fromCache)
    }.getOrNull()

    private fun parseArtists(body: String, fromCache: Boolean): AnonymousArtistProviderResult? = runCatching {
        val values = JSONObject(body).optJSONArray("artists")
            ?: return@runCatching AnonymousArtistProviderResult(emptyList())
        val candidates = buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                val aliases = buildSet {
                    val array = item.optJSONArray("aliases")
                    if (array != null) for (aliasIndex in 0 until array.length()) {
                        array.optString(aliasIndex).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                add(AnonymousArtistCandidate(
                    provider = item.optString("provider", "musicbrainz"),
                    providerItemId = id,
                    displayName = name,
                    sortName = item.optString("sortName"),
                    aliases = aliases,
                    countryCode = item.optString("country"),
                    artistType = artistType(item.optString("type")),
                    artistMbid = item.optString("artistMbid"),
                    avatarUrl = httpsUrl(item.optString("avatarUrl")),
                    providerScore = item.optDouble("score", 0.0).coerceIn(0.0, 1.0),
                    description = item.optString("description").trim()
                        .take(MAX_ARTIST_DESCRIPTION_LENGTH)
                ))
            }
        }
        AnonymousArtistProviderResult(candidates, endpoint, fromCache = fromCache)
    }.getOrNull()

    private fun parseLyrics(body: String, fromCache: Boolean): GatewayLyricsSearchResult? = runCatching {
        val item = JSONObject(body).optJSONObject("lyrics")
            ?: return@runCatching GatewayLyricsSearchResult(null)
        val synced = item.optString("syncedLyrics")
        val plain = item.optString("plainLyrics")
        if (synced.isBlank() && plain.isBlank()) return@runCatching GatewayLyricsSearchResult(null)
        GatewayLyricsSearchResult(
            GatewayLyrics(
                provider = item.optString("provider", "lrclib"),
                id = item.optString("id"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                durationMs = item.optLong("durationMs", 0L),
                syncedLyrics = synced,
                plainLyrics = plain
            )
        )
    }.getOrNull()

    private fun artistType(value: String): ArtistType = runCatching {
        ArtistType.valueOf(value.trim().uppercase())
    }.getOrDefault(ArtistType.UNKNOWN)

    private fun httpsUrl(value: String): String =
        value.trim().takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty()

    private fun trustedCoverUrl(value: String): String = runCatching {
        val uri = java.net.URI(value.trim())
        val host = uri.host?.lowercase().orEmpty()
        value.trim().takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                (host == "coverartarchive.org" || host.endsWith(".mzstatic.com"))
        }.orEmpty()
    }.getOrDefault("")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val PROVIDER = "echo_metadata_gateway"
        const val LYRICS_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val MAX_ARTIST_DESCRIPTION_LENGTH = 1_000

        @JvmStatic
        fun normalizeEndpoint(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return ""
            return trimmed.trimEnd('/') + "/"
        }
    }
}
