package app.yukine.fingerprint

import java.text.Normalizer
import java.util.Locale

/**
 * Recalls suspicious duplicate pairs through bounded hash buckets. It never compares the complete
 * library pairwise: each song contributes to a small fixed set of keys and receives at most
 * [maxCandidatesPerTrack] neighbours.
 */
class FingerprintCandidateBucketBuilder(
    private val maxCandidatesPerTrack: Int = DEFAULT_MAX_CANDIDATES_PER_TRACK
) {
    init {
        require(maxCandidatesPerTrack > 0)
    }

    fun build(tracks: List<FingerprintCandidateTrack>): List<FingerprintCandidatePair> {
        val eligible = tracks.asSequence()
            .filter { it.trackId > 0L && it.locallyReadable && !it.podcastOrAudiobook }
            .distinctBy { it.trackId }
            .toList()
        if (eligible.size < 2) return emptyList()

        val buckets = HashMap<String, MutableList<Long>>(eligible.size * 3)
        val pairs = LinkedHashMap<LongPair, LinkedHashSet<String>>()
        val candidateCounts = HashMap<Long, Int>(eligible.size)
        eligible.forEach { track ->
            keys(track).forEach { key ->
                val existing = buckets.getOrPut(key.value) { ArrayList() }
                for (otherId in existing) {
                    if (candidateCounts.getOrDefault(track.trackId, 0) >= maxCandidatesPerTrack) break
                    if (candidateCounts.getOrDefault(otherId, 0) >= maxCandidatesPerTrack) continue
                    val pair = LongPair.sorted(track.trackId, otherId)
                    val reasons = pairs[pair]
                    if (reasons == null) {
                        pairs[pair] = linkedSetOf(key.reason)
                        candidateCounts[track.trackId] = candidateCounts.getOrDefault(track.trackId, 0) + 1
                        candidateCounts[otherId] = candidateCounts.getOrDefault(otherId, 0) + 1
                    } else {
                        reasons += key.reason
                    }
                }
                existing += track.trackId
            }
        }
        return pairs.map { (pair, reasons) ->
            FingerprintCandidatePair(pair.first, pair.second, reasons)
        }
    }

    private fun keys(track: FingerprintCandidateTrack): List<BucketKey> = buildList {
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        if (title.isNotEmpty() && artist.isNotEmpty()) {
            add(BucketKey("title_artist:$title|$artist", REASON_TITLE_ARTIST))
        }
        if (title.isNotEmpty() && track.durationMs > 0L) {
            add(BucketKey("title_duration:$title|${track.durationMs / DURATION_BUCKET_MS}", REASON_TITLE_DURATION))
        }
        normalizeId(track.isrc).takeIf(String::isNotEmpty)?.let {
            add(BucketKey("isrc:$it", REASON_ISRC))
        }
        normalizeId(track.recordingMbid).takeIf(String::isNotEmpty)?.let {
            add(BucketKey("mbid:$it", REASON_MBID))
        }
        val provider = normalizeId(track.provider)
        val providerTrackId = normalizeId(track.providerTrackId)
        if (provider.isNotEmpty() && providerTrackId.isNotEmpty()) {
            add(BucketKey("provider:$provider|$providerTrackId", REASON_PROVIDER_TRACK))
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(FEATURE_TEXT, " ")
        .replace(NON_ALPHANUMERIC, "")

    private fun normalizeId(value: String): String = value.trim().lowercase(Locale.ROOT)

    private data class BucketKey(val value: String, val reason: String)
    private data class LongPair(val first: Long, val second: Long) {
        companion object {
            fun sorted(left: Long, right: Long): LongPair =
                if (left <= right) LongPair(left, right) else LongPair(right, left)
        }
    }

    companion object {
        const val DEFAULT_MAX_CANDIDATES_PER_TRACK = 50
        const val DURATION_BUCKET_MS = 5_000L
        const val REASON_TITLE_ARTIST = "TITLE_ARTIST"
        const val REASON_TITLE_DURATION = "TITLE_DURATION"
        const val REASON_ISRC = "ISRC"
        const val REASON_MBID = "MBID"
        const val REASON_PROVIDER_TRACK = "PROVIDER_TRACK"

        private val FEATURE_TEXT = Regex("(?i)\\b(feat(?:uring)?|ft)\\.?\\s+[^()\\[\\]]+")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    }
}
