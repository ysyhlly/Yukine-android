package app.yukine.data.enrichment

import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderResponseCacheRepository
import app.yukine.identity.RecordingVariantRecognizer
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class ItunesMetadataClient(
    private val cache: ProviderResponseCacheRepository,
    private val transport: MetadataHttpTransport,
    applicationVersion: String = "unknown",
    contact: String = "https://github.com/ysyhlly/Yukine-android",
    private val now: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = 14L * 24L * 60L * 60L * 1_000L,
    private val retryDelay: (Long) -> Unit = Thread::sleep,
    private val maxAttempts: Int = 2,
    private val refreshScheduler: ((() -> Unit) -> Unit) = { task ->
        Thread(task, "Itunes-SWR").apply {
            isDaemon = true
            start()
        }
    }
) {
    private val userAgent = "EchoAndroid/${applicationVersion.trim()} (${contact.trim()})"
    private val refreshes = ConcurrentHashMap.newKeySet<String>()

    fun searchRecording(title: String, artist: String = "", limit: Int = 12): AnonymousProviderResult {
        require(title.isNotBlank())
        val term = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
        val path = "search?term=${encode(term)}&media=music&entity=song&limit=${limit.coerceIn(1, 200)}"
        val hash = sha256(path)
        val time = now()
        val cached = cache.response(PROVIDER, ENDPOINT, hash, time)
        if (cached?.freshness == ProviderCacheFreshness.FRESH) {
            parse(cached.responseJson)?.let { return AnonymousProviderResult(it, ENDPOINT, fromCache = true) }
        }
        if (cached != null) {
            parse(cached.responseJson)?.let { candidates ->
                scheduleRefresh(path, hash)
                return AnonymousProviderResult(
                    candidates,
                    ENDPOINT,
                    fromCache = true,
                    staleCache = true,
                    allEndpointsFailed = false
                )
            }
        }
        return fetchNetwork(path, hash)
            ?: AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
    }

    private fun scheduleRefresh(path: String, hash: String) {
        if (!refreshes.add(hash)) return
        refreshScheduler {
            try {
                fetchNetwork(path, hash)
            } finally {
                refreshes.remove(hash)
            }
        }
    }

    private fun fetchNetwork(path: String, hash: String): AnonymousProviderResult? {
        val time = now()
        if (cache.endpointHealth(PROVIDER, ENDPOINT).canRequest(time)) {
            var delayMs = INITIAL_RETRY_DELAY_MS
            for (attempt in 0 until maxAttempts.coerceAtLeast(1)) {
                val response = runCatching {
                    transport.get(
                        ENDPOINT + path,
                        mapOf("Accept" to "application/json", "User-Agent" to userAgent)
                    )
                }
                val value = response.getOrNull()
                if (value != null && value.statusCode in 200..299 && value.body.isNotBlank()) {
                    parse(value.body)?.let {
                        cache.saveSuccess(PROVIDER, ENDPOINT, hash, value.body, now(), cacheTtlMs)
                        return AnonymousProviderResult(it, ENDPOINT)
                    }
                }
                cache.recordFailure(
                    PROVIDER,
                    ENDPOINT,
                    response.exceptionOrNull()?.message ?: "HTTP ${value?.statusCode ?: 0}",
                    now()
                )
                val retryable = response.isFailure || value?.statusCode in RETRYABLE_STATUS_CODES
                if (attempt + 1 < maxAttempts && retryable) {
                    retryDelay(delayMs)
                    delayMs = (delayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                } else break
            }
        }
        return null
    }

    private fun parse(body: String): List<AnonymousRecordingCandidate>? = runCatching {
        val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (!item.optString("wrapperType").equals("track", true)) continue
                if (!item.optString("kind").equals("song", true)) continue
                val id = item.optString("trackId")
                val title = item.optString("trackName")
                if (id.isBlank() || title.isBlank()) continue
                val artistId = item.optString("artistId")
                val artistName = item.optString("artistName")
                val album = item.optString("collectionName")
                add(
                    AnonymousRecordingCandidate(
                        provider = PROVIDER,
                        providerItemId = id,
                        title = title,
                        artists = listOfNotNull(
                            ProviderArtistCandidate(artistId, artistName).takeIf {
                                it.providerArtistId.isNotBlank() || it.displayName.isNotBlank()
                            }
                        ),
                        album = album,
                        durationMs = item.optLong("trackTimeMillis", 0L),
                        variantType = RecordingVariantRecognizer.recognize(title, album),
                        providerScore = (1.0 - index * 0.01).coerceAtLeast(0.0)
                    )
                )
            }
        }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PROVIDER = "itunes"
        const val ENDPOINT = "https://itunes.apple.com/"
        const val INITIAL_RETRY_DELAY_MS = 250L
        const val MAX_RETRY_DELAY_MS = 2_000L
        val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }
}
