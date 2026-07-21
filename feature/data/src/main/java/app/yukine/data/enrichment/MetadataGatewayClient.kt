package app.yukine.data.enrichment

import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.AnonymousAlbumCandidate
import app.yukine.identity.AnonymousAlbumProviderResult
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.ArtistType
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalAlbum
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderResponseCacheRepository
import app.yukine.identity.RecordingDuplicateCandidate
import app.yukine.identity.RecordingVariantRecognizer
import app.yukine.identity.WorkCreditEvidence
import app.yukine.identity.WorkIdentifierEvidence
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
    val plainLyrics: String,
    val wordLyrics: String = "",
    val wordLyricsSource: String = "",
    val romanization: String = "",
    val translation: String = ""
)

data class GatewayLyricsSearchResult(
    val lyrics: GatewayLyrics?,
    val allEndpointsFailed: Boolean = false
)

fun interface MetadataGatewayRequestQuota {
    fun tryAcquire(now: Long): Boolean
}

/** Client for the normalized ECHO metadata gateway contract. */
class MetadataGatewayClient(
    private val cache: ProviderResponseCacheRepository,
    private val transport: MetadataHttpTransport,
    endpoint: String,
    applicationVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = 30L * 24L * 60L * 60L * 1_000L,
    private val requestQuota: MetadataGatewayRequestQuota = MetadataGatewayRequestQuota { true }
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
            "recordingMbid" to recording.musicBrainzRecordingId,
            "isrc" to recording.isrc,
            "fingerprint" to fingerprint?.fingerprint.orEmpty(),
            "fingerprintDuration" to fingerprint?.durationSeconds?.toString().orEmpty(),
            "limit" to "12"
        )
        return fetch("v2/recordings/search", params, ::parseRecordings)
            ?: AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
    }

    fun searchArtist(artist: CanonicalArtist, aliases: List<String>): AnonymousArtistProviderResult {
        if (endpoint.isBlank()) return AnonymousArtistProviderResult(emptyList(), allEndpointsFailed = true)
        val params = linkedMapOf(
            "name" to artist.displayName,
            "artistMbid" to artist.musicBrainzArtistId,
            "limit" to "10"
        )
        return fetch("v2/artists/search", params, ::parseArtists)
            ?: AnonymousArtistProviderResult(emptyList(), allEndpointsFailed = true)
    }

    fun searchAlbum(album: CanonicalAlbum, aliases: List<String>): AnonymousAlbumProviderResult {
        if (endpoint.isBlank()) return AnonymousAlbumProviderResult(emptyList(), allEndpointsFailed = true)
        val params = linkedMapOf(
            "title" to album.displayName,
            "artist" to album.albumArtistDisplay,
            "releaseGroupMbid" to album.musicBrainzReleaseGroupId,
            "releaseMbid" to album.musicBrainzReleaseId,
            "year" to album.year.takeIf { it > 0 }?.toString().orEmpty(),
            "type" to album.releaseType,
            "limit" to "10"
        )
        return fetch("v2/albums/search", params, ::parseAlbums)
            ?: AnonymousAlbumProviderResult(emptyList(), allEndpointsFailed = true)
    }

    fun searchLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        neteaseSongId: String = ""
    ): GatewayLyricsSearchResult {
        if (endpoint.isBlank()) return GatewayLyricsSearchResult(null, allEndpointsFailed = true)
        val params = linkedMapOf(
            "title" to title,
            "artist" to artist,
            "album" to album,
            "durationMs" to durationMs.takeIf { it > 0L }?.toString().orEmpty(),
            "neteaseSongId" to neteaseSongId
        )
        return fetch(
            "v2/lyrics/search",
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
        val requestedAt = now()
        val cached = cache.response(PROVIDER, endpoint, requestHash, requestedAt)
        if (cached?.freshness == ProviderCacheFreshness.FRESH) {
            parser(cached.responseJson, true)?.let { return it }
        }
        if (!cache.endpointHealth(PROVIDER, endpoint).canRequest(requestedAt)) {
            return cached?.responseJson?.let { parser(it, true) }
        }
        if (!requestQuota.tryAcquire(requestedAt)) {
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
                cache.saveSuccess(PROVIDER, endpoint, requestHash, value.body, requestedAt, ttlMs)
                return it
            }
        }
        cache.recordFailure(
            PROVIDER,
            endpoint,
            response.exceptionOrNull()?.message ?: "HTTP ${value?.statusCode ?: 0}",
            requestedAt
        )
        return cached?.responseJson?.let { parser(it, true) }
    }

    private fun parseRecordings(body: String, fromCache: Boolean): AnonymousProviderResult? = runCatching {
        val root = JSONObject(body)
        val values = root.optJSONArray("recordings") ?: return@runCatching AnonymousProviderResult(emptyList())
        val candidates = buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val source = sourceIdentity(item)
                val title = item.optString("title")
                if (source.itemId.isBlank() || title.isBlank()) continue
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
                val identifiers = item.optJSONObject("identifiers") ?: JSONObject()
                val legacyIsrc = identifiers.optString("isrc")
                val isrcs = buildSet {
                    legacyIsrc.takeIf(String::isNotBlank)?.let(::add)
                    sequenceOf(
                        item.optJSONArray("isrcs"),
                        identifiers.optJSONArray("isrcs")
                    ).filterNotNull().forEach { array ->
                        for (isrcIndex in 0 until array.length()) {
                            array.optString(isrcIndex).trim()
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }
                val legacyWorkMbid = identifiers.optString("workMbid")
                val workIdentifiers = buildList {
                    sequenceOf(
                        item.optJSONArray("workIdentifiers"),
                        identifiers.optJSONArray("workIdentifiers")
                    ).filterNotNull().forEach { array ->
                        for (identifierIndex in 0 until array.length()) {
                            val value = array.optJSONObject(identifierIndex) ?: continue
                            val type = value.optString("type").trim().uppercase()
                            val identifierValue = value.optString("value").trim()
                            if (type.isBlank() || identifierValue.isBlank()) continue
                            add(
                                WorkIdentifierEvidence(
                                    identifierType = type,
                                    namespace = value.optString("namespace").trim(),
                                    identifierValue = identifierValue,
                                    source = value.optString("source", source.provider).trim(),
                                    confidence = value.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                                    verifiedAt = value.optLong("verifiedAt", 0L).coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                    if (legacyWorkMbid.isNotBlank() && none {
                            it.identifierType == "MUSICBRAINZ_WORK_ID" &&
                                it.identifierValue.equals(legacyWorkMbid, ignoreCase = true)
                        }
                    ) {
                        add(
                            WorkIdentifierEvidence(
                                identifierType = "MUSICBRAINZ_WORK_ID",
                                identifierValue = legacyWorkMbid,
                                source = source.provider,
                                confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                            )
                        )
                    }
                }
                val workCredits = buildList {
                    val array = item.optJSONArray("workCredits")
                    if (array != null) for (creditIndex in 0 until array.length()) {
                        val value = array.optJSONObject(creditIndex) ?: continue
                        val name = value.optString("name").trim()
                        val role = value.optString("role").trim().uppercase()
                        if (name.isBlank() || role.isBlank()) continue
                        add(
                            WorkCreditEvidence(
                                providerArtistId = value.optString("artistId").trim(),
                                creditedName = name,
                                role = role,
                                source = value.optString("source", source.provider).trim(),
                                confidence = value.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                                verifiedAt = value.optLong("verifiedAt", 0L).coerceAtLeast(0L)
                            )
                        )
                    }
                }
                val possibleDuplicates = buildList {
                    val duplicatesArray = item.optJSONArray("possibleDuplicates")
                    if (duplicatesArray != null) for (dupIndex in 0 until duplicatesArray.length()) {
                        val dup = duplicatesArray.optJSONObject(dupIndex) ?: continue
                        val dupCanonicalId = dup.optString("canonicalId").trim()
                        if (dupCanonicalId.isBlank()) continue
                        add(RecordingDuplicateCandidate(
                            canonicalId = dupCanonicalId,
                            confidence = dup.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                        ))
                    }
                }
                add(AnonymousRecordingCandidate(
                    provider = source.provider,
                    providerItemId = source.itemId,
                    title = title,
                    artists = artists,
                    album = item.optString("album"),
                    durationMs = item.optLong("durationMs", 0L),
                    isrc = legacyIsrc.ifBlank { isrcs.firstOrNull().orEmpty() },
                    isrcs = isrcs,
                    recordingMbid = identifiers.optString("recordingMbid"),
                    workMbid = legacyWorkMbid,
                    workIdentifiers = workIdentifiers,
                    workCredits = workCredits,
                    acoustId = identifiers.optString("acoustId"),
                    coverUrl = trustedCoverUrl(item.optString("coverUrl")),
                    fingerprintVerified = item.optBoolean("fingerprintVerified", false),
                    variantType = RecordingVariantRecognizer.recognize(title, item.optString("album")),
                    providerScore = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                    possibleDuplicates = possibleDuplicates
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
                val source = sourceIdentity(item)
                val name = item.optString("name")
                if (source.itemId.isBlank() || name.isBlank()) continue
                val aliases = buildSet {
                    val array = item.optJSONArray("aliases")
                    if (array != null) for (aliasIndex in 0 until array.length()) {
                        array.optString(aliasIndex).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                val identifiers = item.optJSONObject("identifiers") ?: JSONObject()
                add(AnonymousArtistCandidate(
                    provider = source.provider,
                    providerItemId = source.itemId,
                    displayName = name,
                    sortName = item.optString("sortName"),
                    aliases = aliases,
                    countryCode = item.optString("country"),
                    artistType = artistType(item.optString("type")),
                    artistMbid = identifiers.optString("artistMbid"),
                    avatarUrl = httpsUrl(item.optString("avatarUrl")),
                    providerScore = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                    description = item.optString("description").trim()
                        .take(MAX_ARTIST_DESCRIPTION_LENGTH),
                    biography = item.optString("biography").trim()
                        .take(MAX_ARTIST_BIOGRAPHY_LENGTH)
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
        val wordLyrics = item.optString("wordLyrics")
            .take(MAX_WORD_LYRICS_LENGTH)
            .takeIf { it.length < MAX_WORD_LYRICS_LENGTH } ?: ""
        val wordLyricsSource = item.optString("wordLyricsSource")
        val romanization = item.optString("romanization")
            .take(MAX_WORD_LYRICS_LENGTH)
            .takeIf { it.length < MAX_WORD_LYRICS_LENGTH } ?: ""
        val translation = item.optString("translation")
            .take(MAX_WORD_LYRICS_LENGTH)
            .takeIf { it.length < MAX_WORD_LYRICS_LENGTH } ?: ""
        if (synced.isBlank() && plain.isBlank() && wordLyrics.isBlank()) {
            return@runCatching GatewayLyricsSearchResult(null)
        }
        val source = sourceIdentity(item)
        val canonicalId = item.optString("canonicalId").trim()
        GatewayLyricsSearchResult(
            GatewayLyrics(
                provider = source.provider,
                id = canonicalId.ifBlank { source.itemId },
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                durationMs = item.optLong("durationMs", 0L),
                syncedLyrics = synced,
                plainLyrics = plain,
                wordLyrics = wordLyrics,
                wordLyricsSource = wordLyricsSource,
                romanization = romanization,
                translation = translation
            )
        )
    }.getOrNull()

    private fun parseAlbums(body: String, fromCache: Boolean): AnonymousAlbumProviderResult? = runCatching {
        val values = JSONObject(body).optJSONArray("albums")
            ?: return@runCatching AnonymousAlbumProviderResult(emptyList())
        val candidates = buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val source = sourceIdentity(item)
                val title = item.optString("title").trim()
                if (source.itemId.isBlank() || title.isBlank()) continue
                val aliases = buildSet {
                    val array = item.optJSONArray("aliases")
                    if (array != null) for (aliasIndex in 0 until array.length()) {
                        array.optString(aliasIndex).trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                val artists = item.optJSONArray("artists")
                val artist = item.optString("artist").trim().ifBlank {
                    artists?.optJSONObject(0)?.optString("name")?.trim().orEmpty()
                }
                val identifiers = item.optJSONObject("identifiers") ?: JSONObject()
                add(
                    AnonymousAlbumCandidate(
                        provider = source.provider,
                        providerAlbumId = source.itemId,
                        title = title,
                        aliases = aliases,
                        artist = artist,
                        musicBrainzReleaseGroupId = identifiers.optString("releaseGroupMbid").trim(),
                        musicBrainzReleaseId = identifiers.optString("releaseMbid").trim(),
                        releaseType = item.optString("type").trim(),
                        year = item.optInt("year", 0).coerceAtLeast(0),
                        providerScore = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                    )
                )
            }
        }
        AnonymousAlbumProviderResult(candidates, endpoint, fromCache = fromCache)
    }.getOrNull()

    private fun sourceIdentity(item: JSONObject): GatewaySourceIdentity {
        val sources = item.optJSONArray("sources")
        var firstValid: GatewaySourceIdentity? = null
        if (sources != null) {
            for (index in 0 until sources.length()) {
                val source = sources.optJSONObject(index) ?: continue
                val provider = source.optString("provider").trim()
                val itemId = source.optString("id").trim()
                if (provider.isBlank() || itemId.isBlank()) continue
                val identity = GatewaySourceIdentity(provider, itemId)
                if (source.optString("role").equals("identity", ignoreCase = true)) {
                    return identity
                }
                if (firstValid == null) firstValid = identity
            }
        }
        return firstValid ?: GatewaySourceIdentity(
            provider = PROVIDER,
            itemId = item.optString("canonicalId").trim()
        )
    }

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
        private const val MAX_ARTIST_BIOGRAPHY_LENGTH = 4_096
        private const val MAX_WORD_LYRICS_LENGTH = 512 * 1024

        @JvmStatic
        fun normalizeEndpoint(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return ""
            return trimmed.trimEnd('/') + "/"
        }
    }

    private data class GatewaySourceIdentity(
        val provider: String,
        val itemId: String
    )
}
