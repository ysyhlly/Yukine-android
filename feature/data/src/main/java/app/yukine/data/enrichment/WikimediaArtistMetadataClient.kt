package app.yukine.data.enrichment

import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistType
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderResponseCacheRepository
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** Background-only Wikidata and Wikipedia artist candidate lookup. */
class WikimediaArtistMetadataClient(
    private val cache: ProviderResponseCacheRepository,
    private val transport: MetadataHttpTransport,
    applicationVersion: String,
    contact: String,
    private val rateLimiter: RequestRateLimiter = OneRequestPerSecondRateLimiter(250_000_000L),
    private val now: () -> Long = System::currentTimeMillis,
    private val retryDelay: (Long) -> Unit = Thread::sleep,
    private val cacheTtlMs: Long = 30L * 24L * 60L * 60L * 1_000L,
    private val maxAttemptsPerEndpoint: Int = 2,
    private val refreshScheduler: ((() -> Unit) -> Unit) = { task ->
        Thread(task, "Wikimedia-SWR").apply {
            isDaemon = true
            start()
        }
    }
) {
    private val userAgent: String
    private val refreshes = ConcurrentHashMap.newKeySet<String>()

    init {
        require(applicationVersion.isNotBlank())
        require(contact.isNotBlank())
        userAgent = "EchoAndroid/${applicationVersion.trim()} (${contact.trim()})"
    }

    fun searchArtist(name: String, aliases: List<String> = emptyList()): AnonymousArtistProviderResult {
        require(name.isNotBlank())
        val language = preferredLanguage(name, aliases)
        val requests = listOf(
            RequestSpec(
                provider = WIKIDATA_PROVIDER,
                endpoint = WIKIDATA_ENDPOINT,
                path = wikidataPath(name, language),
                parser = ::parseWikidataArtists
            ),
            RequestSpec(
                provider = WIKIPEDIA_PROVIDER,
                endpoint = "https://$language.wikipedia.org/w/api.php",
                path = wikipediaPath(name),
                parser = { body -> parseWikipediaArtists(body, language) }
            )
        )
        val fetched = requests.mapNotNull(::fetch)
        val candidates = fetched
            .flatMap(Fetched::candidates)
            .distinctBy { it.provider to it.providerItemId }
        return AnonymousArtistProviderResult(
            candidates = candidates,
            endpoint = fetched.map(Fetched::endpoint).distinct().joinToString(","),
            fromCache = fetched.any(Fetched::fromCache),
            staleCache = fetched.any(Fetched::staleCache),
            allEndpointsFailed = fetched.none { !it.staleCache }
        )
    }

    private fun fetch(spec: RequestSpec): Fetched? {
        val requestHash = sha256("${spec.provider}|${spec.path}")
        val cached = cache.response(CACHE_PROVIDER, spec.endpoint, requestHash, now())
        if (cached?.freshness == ProviderCacheFreshness.FRESH) {
            spec.parser(cached.responseJson)?.let { candidates ->
                return Fetched(candidates, spec.endpoint, fromCache = true)
            }
        }
        if (cached != null) {
            spec.parser(cached.responseJson)?.let { candidates ->
                scheduleRefresh(spec, requestHash)
                return Fetched(candidates, spec.endpoint, fromCache = true, staleCache = true)
            }
        }
        return fetchNetwork(spec, requestHash)
    }

    private fun scheduleRefresh(spec: RequestSpec, requestHash: String) {
        if (!refreshes.add(requestHash)) return
        refreshScheduler {
            try {
                fetchNetwork(spec, requestHash)
            } finally {
                refreshes.remove(requestHash)
            }
        }
    }

    private fun fetchNetwork(spec: RequestSpec, requestHash: String): Fetched? {
        if (!cache.endpointHealth(CACHE_PROVIDER, spec.endpoint).canRequest(now())) return null
        var delayMs = INITIAL_RETRY_DELAY_MS
        for (attempt in 0 until maxAttemptsPerEndpoint.coerceAtLeast(1)) {
            val result = runCatching {
                rateLimiter.awaitPermit()
                transport.get(
                    spec.endpoint + spec.path,
                    mapOf("Accept" to "application/json", "User-Agent" to userAgent)
                )
            }
            val response = result.getOrNull()
            if (response != null && response.statusCode in 200..299 && response.body.isNotBlank()) {
                val candidates = spec.parser(response.body)
                if (candidates != null) {
                    val time = now()
                    cache.saveSuccess(
                        CACHE_PROVIDER,
                        spec.endpoint,
                        requestHash,
                        response.body,
                        time,
                        cacheTtlMs
                    )
                    return Fetched(candidates, spec.endpoint)
                }
            }
            val error = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                ?: "HTTP ${response?.statusCode ?: 0}"
            cache.recordFailure(CACHE_PROVIDER, spec.endpoint, error, now())
            val retryable = result.isFailure || response?.statusCode in RETRYABLE_STATUS_CODES
            if (attempt + 1 < maxAttemptsPerEndpoint && retryable) {
                retryDelay(delayMs)
                delayMs = (delayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
            } else {
                break
            }
        }
        return null
    }

    private fun parseWikidataArtists(body: String): List<AnonymousArtistCandidate>? = runCatching {
        val values = JSONObject(body).optJSONArray("search") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index) ?: continue
                val id = value.optString("id").trim()
                val label = value.optString("label").trim()
                if (!id.matches(WIKIDATA_ID) || label.isBlank()) continue
                val aliases = buildSet {
                    add(label)
                    val rawAliases = value.optJSONArray("aliases")
                    if (rawAliases != null) for (aliasIndex in 0 until rawAliases.length()) {
                        rawAliases.optString(aliasIndex).trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                    value.optJSONObject("match")?.optString("text")?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
                add(
                    AnonymousArtistCandidate(
                        provider = WIKIDATA_PROVIDER,
                        providerItemId = id,
                        displayName = label,
                        aliases = aliases,
                        artistType = artistTypeFromDescription(value.optString("description")),
                        providerScore = (0.90 - index * 0.04).coerceAtLeast(0.50)
                    )
                )
            }
        }
    }.getOrNull()

    private fun parseWikipediaArtists(body: String, language: String): List<AnonymousArtistCandidate>? = runCatching {
        val values = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
            ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index) ?: continue
                val pageId = value.optLong("pageid", 0L)
                val title = value.optString("title").trim()
                if (pageId <= 0L || title.isBlank()) continue
                add(
                    AnonymousArtistCandidate(
                        provider = WIKIPEDIA_PROVIDER,
                        providerItemId = "$language:$pageId",
                        displayName = title,
                        aliases = setOf(title),
                        artistType = artistTypeFromDescription(value.optString("snippet")),
                        providerScore = (0.65 - index * 0.04).coerceAtLeast(0.35)
                    )
                )
            }
        }
    }.getOrNull()

    private fun artistTypeFromDescription(value: String): ArtistType {
        val normalized = value.lowercase()
        return when {
            listOf("orchestra", "管弦", "オーケストラ").any(normalized::contains) -> ArtistType.ORCHESTRA
            listOf("choir", "合唱", "コーラス").any(normalized::contains) -> ArtistType.CHOIR
            listOf("virtual", "虚拟", "虛擬", "バーチャル").any(normalized::contains) -> ArtistType.VIRTUAL
            listOf("character", "角色", "キャラクター").any(normalized::contains) -> ArtistType.CHARACTER
            listOf("band", "group", "duo", "trio", "组合", "組合", "乐队", "樂隊", "バンド").any(normalized::contains) -> ArtistType.GROUP
            listOf("singer", "musician", "rapper", "composer", "歌手", "音乐家", "音樂家", "歌い手", "声優").any(normalized::contains) -> ArtistType.PERSON
            else -> ArtistType.UNKNOWN
        }
    }

    private fun preferredLanguage(name: String, aliases: List<String>): String {
        val value = (listOf(name) + aliases).joinToString(" ")
        return when {
            value.any { it in '\u3040'..'\u30ff' } -> "ja"
            value.any { it in '\u3400'..'\u9fff' } -> "zh"
            else -> "en"
        }
    }

    private fun wikidataPath(name: String, language: String): String =
        "?action=wbsearchentities&format=json&formatversion=2&type=item&limit=8" +
            "&language=${encode(language)}&uselang=${encode(language)}&search=${encode(name)}"

    private fun wikipediaPath(name: String): String =
        "?action=query&format=json&formatversion=2&list=search&srnamespace=0&srlimit=6&srsearch=${encode(name)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class RequestSpec(
        val provider: String,
        val endpoint: String,
        val path: String,
        val parser: (String) -> List<AnonymousArtistCandidate>?
    )

    private data class Fetched(
        val candidates: List<AnonymousArtistCandidate>,
        val endpoint: String,
        val fromCache: Boolean = false,
        val staleCache: Boolean = false
    )

    private companion object {
        const val CACHE_PROVIDER = "wikimedia"
        const val WIKIDATA_PROVIDER = "wikidata"
        const val WIKIPEDIA_PROVIDER = "wikipedia"
        const val WIKIDATA_ENDPOINT = "https://www.wikidata.org/w/api.php"
        const val INITIAL_RETRY_DELAY_MS = 250L
        const val MAX_RETRY_DELAY_MS = 2_000L
        val WIKIDATA_ID = Regex("Q[1-9][0-9]*")
        val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }
}
