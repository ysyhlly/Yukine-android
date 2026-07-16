package app.yukine.data.enrichment

import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistType
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderResponseCacheRepository
import app.yukine.identity.RecordingVariantRecognizer
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class RecordingMetadataQuery(
    val title: String,
    val artist: String = "",
    val isrc: String = "",
    val limit: Int = 12
)

class MusicBrainzMetadataClient(
    private val cache: ProviderResponseCacheRepository,
    private val transport: MetadataHttpTransport,
    applicationVersion: String,
    contact: String,
    customProxy: String = "",
    private val rateLimiter: RequestRateLimiter = MusicBrainzRequestRateLimiter,
    private val now: () -> Long = System::currentTimeMillis,
    private val retryDelay: (Long) -> Unit = Thread::sleep,
    private val cacheTtlMs: Long = 30L * 24L * 60L * 60L * 1_000L,
    private val maxAttemptsPerEndpoint: Int = 2,
    private val refreshScheduler: ((() -> Unit) -> Unit) = { task ->
        Thread(task, "MusicBrainz-SWR").apply {
            isDaemon = true
            start()
        }
    }
) {
    private val userAgent: String
    private val refreshes = ConcurrentHashMap.newKeySet<String>()
    val endpoints: List<String>

    init {
        require(applicationVersion.isNotBlank())
        require(contact.isNotBlank())
        userAgent = "EchoAndroid/${applicationVersion.trim()} (${contact.trim()})"
        endpoints = listOf(OFFICIAL_ENDPOINT, EU_ENDPOINT, customProxy)
            .mapNotNull(::normalizeEndpoint)
            .distinct()
    }

    fun searchRecording(query: RecordingMetadataQuery): AnonymousProviderResult {
        require(query.title.isNotBlank() || query.isrc.isNotBlank())
        val path = requestPath(query)
        val fetched = fetch(path, ::parseRecordings)
            ?: return AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
        return AnonymousProviderResult(
            candidates = fetched.value,
            endpoint = fetched.endpoint,
            fromCache = fetched.fromCache,
            staleCache = fetched.staleCache,
            allEndpointsFailed = false
        )
    }

    fun searchArtist(name: String, limit: Int = 10): AnonymousArtistProviderResult {
        require(name.isNotBlank())
        val path = "artist/?query=${encode("artist:\"${escapeLucene(name)}\"")}" +
            "&limit=${limit.coerceIn(1, 100)}&fmt=json"
        val fetched = fetch(path, ::parseArtists)
            ?: return AnonymousArtistProviderResult(emptyList(), allEndpointsFailed = true)
        return AnonymousArtistProviderResult(
            candidates = fetched.value,
            endpoint = fetched.endpoint,
            fromCache = fetched.fromCache,
            staleCache = fetched.staleCache,
            allEndpointsFailed = false
        )
    }

    private fun <T> fetch(path: String, parser: (String) -> T?): Fetched<T>? {
        val requestHash = sha256(path)
        var stale: Pair<String, String>? = null

        for (endpoint in endpoints) {
            val cached = cache.response(PROVIDER, endpoint, requestHash, now())
            if (cached != null && cached.freshness == ProviderCacheFreshness.FRESH) {
                parser(cached.responseJson)?.let {
                    return Fetched(it, endpoint, fromCache = true)
                }
            } else if (cached != null && stale == null) {
                stale = endpoint to cached.responseJson
            }
        }

        stale?.let { (endpoint, json) ->
            parser(json)?.let { value ->
                scheduleRefresh(path, requestHash, parser)
                return Fetched(value, endpoint, fromCache = true, staleCache = true)
            }
        }

        return fetchNetwork(path, requestHash, parser)
    }

    private fun <T> scheduleRefresh(path: String, requestHash: String, parser: (String) -> T?) {
        if (!refreshes.add(requestHash)) return
        refreshScheduler {
            try {
                fetchNetwork(path, requestHash, parser)
            } finally {
                refreshes.remove(requestHash)
            }
        }
    }

    private fun <T> fetchNetwork(path: String, requestHash: String, parser: (String) -> T?): Fetched<T>? {
        for (endpoint in endpoints) {

            if (!cache.endpointHealth(PROVIDER, endpoint).canRequest(now())) continue
            var delayMs = INITIAL_RETRY_DELAY_MS
            for (attempt in 0 until maxAttemptsPerEndpoint.coerceAtLeast(1)) {
                val result = runCatching {
                    rateLimiter.awaitPermit()
                    transport.get(
                        endpoint + path,
                        mapOf("Accept" to "application/json", "User-Agent" to userAgent)
                    )
                }
                val response = result.getOrNull()
                if (response != null && response.statusCode in 200..299 && response.body.isNotBlank()) {
                    val parsed = parser(response.body)
                    if (parsed != null) {
                        val time = now()
                        cache.saveSuccess(PROVIDER, endpoint, requestHash, response.body, time, cacheTtlMs)
                        return Fetched(parsed, endpoint)
                    }
                }

                val error = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                    ?: "HTTP ${response?.statusCode ?: 0}"
                cache.recordFailure(PROVIDER, endpoint, error, now())
                val retryable = result.isFailure || response?.statusCode in RETRYABLE_STATUS_CODES
                if (attempt + 1 < maxAttemptsPerEndpoint && retryable) {
                    retryDelay(delayMs)
                    delayMs = (delayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                } else {
                    break
                }
            }
        }
        return null
    }

    private fun requestPath(query: RecordingMetadataQuery): String {
        val normalizedIsrc = query.isrc.filter(Char::isLetterOrDigit).uppercase()
        if (normalizedIsrc.length == 12) {
            return "isrc/${encode(normalizedIsrc)}?inc=artist-credits&fmt=json"
        }
        val clauses = buildList {
            add("recording:\"${escapeLucene(query.title)}\"")
            if (query.artist.isNotBlank()) add("artist:\"${escapeLucene(query.artist)}\"")
        }
        return "recording/?query=${encode(clauses.joinToString(" AND "))}&limit=${query.limit.coerceIn(1, 100)}&fmt=json"
    }

    private fun parseRecordings(body: String): List<AnonymousRecordingCandidate>? = runCatching {
        val root = JSONObject(body)
        val recordings = root.optJSONArray("recordings") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(index) ?: continue
                val id = recording.optString("id")
                val title = recording.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                val artists = buildList {
                    val credits = recording.optJSONArray("artist-credit")
                    if (credits != null) for (creditIndex in 0 until credits.length()) {
                        val credit = credits.optJSONObject(creditIndex) ?: continue
                        val artist = credit.optJSONObject("artist") ?: continue
                        val artistId = artist.optString("id")
                        val name = credit.optString("name").ifBlank { artist.optString("name") }
                        if (artistId.isNotBlank() || name.isNotBlank()) {
                            add(ProviderArtistCandidate(artistId, name, artist.optString("sort-name")))
                        }
                    }
                }
                val isrcs = recording.optJSONArray("isrcs")
                val releases = recording.optJSONArray("releases")
                val album = releases?.optJSONObject(0)?.optString("title").orEmpty()
                val isrc = if (isrcs != null && isrcs.length() > 0) isrcs.optString(0) else ""
                add(
                    AnonymousRecordingCandidate(
                        provider = PROVIDER,
                        providerItemId = id,
                        title = title,
                        artists = artists,
                        album = album,
                        durationMs = recording.optLong("length", 0L),
                        isrc = isrc,
                        recordingMbid = id,
                        variantType = RecordingVariantRecognizer.recognize(title, album),
                        providerScore = recording.optDouble("score", 0.0) / 100.0
                    )
                )
            }
        }
    }.getOrNull()

    private fun parseArtists(body: String): List<AnonymousArtistCandidate>? = runCatching {
        val artists = JSONObject(body).optJSONArray("artists") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until artists.length()) {
                val artist = artists.optJSONObject(index) ?: continue
                val id = artist.optString("id")
                val name = artist.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                val aliases = buildSet {
                    val values = artist.optJSONArray("aliases")
                    if (values != null) for (aliasIndex in 0 until values.length()) {
                        val alias = values.optJSONObject(aliasIndex)?.optString("name").orEmpty()
                        if (alias.isNotBlank()) add(alias)
                    }
                }
                add(
                    AnonymousArtistCandidate(
                        provider = PROVIDER,
                        providerItemId = id,
                        displayName = name,
                        sortName = artist.optString("sort-name"),
                        aliases = aliases,
                        countryCode = artist.optString("country"),
                        artistType = artistType(artist.optString("type")),
                        artistMbid = id,
                        providerScore = artist.optDouble("score", 0.0) / 100.0
                    )
                )
            }
        }
    }.getOrNull()

    private fun artistType(value: String): ArtistType = when (value.trim().lowercase()) {
        "person" -> ArtistType.PERSON
        "group" -> ArtistType.GROUP
        "orchestra" -> ArtistType.ORCHESTRA
        "choir" -> ArtistType.CHOIR
        "character" -> ArtistType.CHARACTER
        else -> ArtistType.UNKNOWN
    }

    private fun normalizeEndpoint(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        return trimmed.trimEnd('/') + "/"
    }

    private fun escapeLucene(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class Fetched<T>(
        val value: T,
        val endpoint: String,
        val fromCache: Boolean = false,
        val staleCache: Boolean = false
    )

    private companion object {
        const val PROVIDER = "musicbrainz"
        const val OFFICIAL_ENDPOINT = "https://musicbrainz.org/ws/2/"
        const val EU_ENDPOINT = "https://musicbrainz.eu/ws/2/"
        const val INITIAL_RETRY_DELAY_MS = 250L
        const val MAX_RETRY_DELAY_MS = 2_000L
        val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }
}
