package app.yukine.data

import app.yukine.streaming.StreamingTrackMatchPolicy
import kotlin.math.sqrt

internal data class MetadataEmbedding(
    val vector: ByteArray,
    val simHash: Long,
    val version: Int = MetadataHashEmbeddingEncoder.VECTOR_VERSION
)

internal object MetadataHashEmbeddingEncoder {
    const val VECTOR_VERSION = 1
    const val DIMENSIONS = 64
    private const val YEAR_BUCKET_SIZE = 5

    fun encode(
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        versionSignature: String,
        releaseType: String,
        year: Int
    ): MetadataEmbedding? {
        val tokens = linkedSetOf<String>()
        addTokens(tokens, "title", StreamingTrackMatchPolicy.canonicalTitle(title))
        addTokens(tokens, "artist", StreamingTrackMatchPolicy.canonicalArtistKey(listOf(artist)))
        addTokens(tokens, "album", StreamingTrackMatchPolicy.canonicalAlbum(album))
        addTokens(
            tokens,
            "album_artist",
            StreamingTrackMatchPolicy.canonicalArtistKey(listOf(albumArtist))
        )
        addTokens(
            tokens,
            "composer",
            StreamingTrackMatchPolicy.canonicalArtistKey(listOf(composer))
        )
        addTokens(tokens, "version", versionSignature)
        addTokens(tokens, "release_type", releaseType)
        if (year in 1000..9999) {
            tokens += "year_bucket:${year / YEAR_BUCKET_SIZE}"
        }
        if (tokens.isEmpty()) return null

        val accumulators = IntArray(DIMENSIONS)
        tokens.sorted().forEach { token ->
            val hash = avalanche(token.hashCode())
            val index = hash and (DIMENSIONS - 1)
            val sign = if ((hash ushr 6) and 1 == 0) 1 else -1
            accumulators[index] += sign
        }
        val vector = ByteArray(DIMENSIONS) { index ->
            accumulators[index].coerceIn(Byte.MIN_VALUE.toInt() + 1, Byte.MAX_VALUE.toInt()).toByte()
        }
        if (vector.all { it.toInt() == 0 }) return null
        var simHash = 0L
        vector.indices.forEach { index ->
            if (vector[index] > 0) {
                simHash = simHash or (1L shl index)
            }
        }
        return MetadataEmbedding(vector, simHash)
    }

    fun cosine(left: ByteArray?, right: ByteArray?): Double {
        if (left == null || right == null || left.size != DIMENSIONS || right.size != DIMENSIONS) {
            return 0.0
        }
        var dot = 0L
        var leftNorm = 0L
        var rightNorm = 0L
        for (index in 0 until DIMENSIONS) {
            val leftValue = left[index].toLong()
            val rightValue = right[index].toLong()
            dot += leftValue * rightValue
            leftNorm += leftValue * leftValue
            rightNorm += rightValue * rightValue
        }
        if (leftNorm == 0L || rightNorm == 0L) return 0.0
        return (dot / sqrt(leftNorm.toDouble() * rightNorm.toDouble())).coerceIn(0.0, 1.0)
    }

    private fun addTokens(destination: MutableSet<String>, field: String, value: String) {
        value.trim()
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .filter(String::isNotBlank)
            .forEach { token -> destination += "$field:$token" }
    }

    private fun avalanche(value: Int): Int {
        var hash = value
        hash = hash xor (hash ushr 16)
        hash *= 0x85ebca6b.toInt()
        hash = hash xor (hash ushr 13)
        hash *= 0xc2b2ae35.toInt()
        return hash xor (hash ushr 16)
    }
}
