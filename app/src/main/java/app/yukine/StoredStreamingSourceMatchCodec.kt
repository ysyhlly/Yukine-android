package app.yukine

import app.yukine.streaming.StreamingTrack
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredStreamingSourceCandidate(
    val providerTrackId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val luoxueMusicInfoJson: String? = null,
    val isrc: String? = null
)

internal data class StoredStreamingSourceMatch(
    val primaryProviderTrackId: String,
    val candidates: List<StoredStreamingSourceCandidate>
) {
    companion object {
        fun legacy(providerTrackId: String): StoredStreamingSourceMatch {
            val cleanId = providerTrackId.trim()
            return StoredStreamingSourceMatch(
                primaryProviderTrackId = cleanId,
                candidates = listOf(StoredStreamingSourceCandidate(cleanId))
            )
        }
    }
}

/** Compact, backward-compatible persistence for one canonical match and its version choices. */
internal object StoredStreamingSourceMatchCodec {
    private const val LEGACY_PREFIX = "__echo_source_match_v1__:"
    private const val PREFIX = "__echo_source_match_v2__:"
    private const val MAX_CANDIDATES = 12

    fun isEncoded(value: String?): Boolean = value?.let {
        it.startsWith(PREFIX) || it.startsWith(LEGACY_PREFIX)
    } == true

    fun upgradedEncoding(value: String?): String? {
        val clean = value.orEmpty().trim()
        if (!clean.startsWith(LEGACY_PREFIX)) return null
        return PREFIX + clean.removePrefix(LEGACY_PREFIX)
    }

    fun primaryProviderTrackId(value: String?): String {
        val clean = value.orEmpty().trim()
        if (clean.isBlank() || isStreamingNoSourceMatch(clean)) return ""
        return decode(clean)?.primaryProviderTrackId.orEmpty().ifBlank {
            clean.takeUnless(::isEncoded).orEmpty()
        }
    }

    fun encode(primary: StreamingTrack, orderedCandidates: List<StreamingTrack>): String {
        val primaryId = primary.providerTrackId.trim()
        if (primaryId.isBlank()) return ""
        val candidates = (listOf(primary) + orderedCandidates)
            .asSequence()
            .filter { it.providerTrackId.isNotBlank() }
            .distinctBy { it.providerTrackId.trim() }
            .take(MAX_CANDIDATES)
            .toList()
        val array = JSONArray()
        candidates.forEach { track -> array.put(candidateJson(track)) }
        return PREFIX + JSONObject()
            .put("primary", primaryId)
            .put("candidates", array)
            .toString()
    }

    fun decode(value: String?): StoredStreamingSourceMatch? {
        val clean = value.orEmpty().trim()
        if (clean.isBlank() || isStreamingNoSourceMatch(clean)) return null
        if (!isEncoded(clean)) return StoredStreamingSourceMatch.legacy(clean)
        return runCatching {
            val json = when {
                clean.startsWith(PREFIX) -> clean.removePrefix(PREFIX)
                else -> clean.removePrefix(LEGACY_PREFIX)
            }
            val root = JSONObject(json)
            val primary = root.optString("primary").trim()
            if (primary.isBlank()) return@runCatching null
            val array = root.optJSONArray("candidates") ?: JSONArray()
            val decoded = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::candidate)
            }
            val primaryCandidate = decoded.firstOrNull { it.providerTrackId == primary }
                ?: StoredStreamingSourceCandidate(primary)
            val candidates = (listOf(primaryCandidate) + decoded)
                .distinctBy(StoredStreamingSourceCandidate::providerTrackId)
                .take(MAX_CANDIDATES)
            StoredStreamingSourceMatch(primary, candidates)
        }.getOrNull()
    }

    private fun candidateJson(track: StreamingTrack): JSONObject = JSONObject()
        .put("id", track.providerTrackId.trim())
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("durationMs", track.durationMs)
        .put("coverUrl", track.coverThumbUrl ?: track.coverUrl)
        .put("musicInfo", track.luoxueMusicInfoJson)
        .put("isrc", track.isrc)

    private fun candidate(value: JSONObject): StoredStreamingSourceCandidate? {
        val providerTrackId = value.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
        return StoredStreamingSourceCandidate(
            providerTrackId = providerTrackId,
            title = value.optString("title").trim(),
            artist = value.optString("artist").trim(),
            album = value.optString("album").trim(),
            durationMs = value.optLong("durationMs").takeIf { it > 0L },
            coverUrl = value.optString("coverUrl").trim().takeIf { it.isNotBlank() },
            luoxueMusicInfoJson = value.optString("musicInfo").trim().takeIf { it.isNotBlank() },
            isrc = value.optString("isrc").trim().takeIf { it.isNotBlank() }
        )
    }
}
