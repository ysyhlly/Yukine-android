package app.yukine.data

internal data class EmbeddingIndexItem(
    val sourceId: Long,
    val simHash: Long
)

internal data class EmbeddingRecallHit(
    val sourceId: Long,
    val bandMatches: Int
)

/** In-memory SimHash LSH index. It is rebuilt for each identity candidate snapshot. */
internal class EmbeddingPostingIndex(items: Collection<EmbeddingIndexItem>) {
    private val postings = HashMap<Long, MutableList<Long>>()

    init {
        items.sortedBy(EmbeddingIndexItem::sourceId).forEach { item ->
            repeat(BAND_COUNT) { band ->
                postings.getOrPut(bandKey(item.simHash, band)) { ArrayList() } += item.sourceId
            }
        }
    }

    fun recall(simHash: Long, querySourceId: Long): List<EmbeddingRecallHit> {
        val matches = HashMap<Long, Int>()
        repeat(BAND_COUNT) { band ->
            val value = bandValue(simHash, band)
            val probes = IntArray(BITS_PER_BAND + 1)
            probes[0] = value
            repeat(BITS_PER_BAND) { bit ->
                probes[bit + 1] = value xor (1 shl bit)
            }
            probes.forEach { probe ->
                val rows = postings[bandKey(probe, band)].orEmpty()
                if (rows.size > MAX_POSTING_SIZE) return@forEach
                rows.forEach { sourceId ->
                    if (sourceId != querySourceId) {
                        matches[sourceId] = (matches[sourceId] ?: 0) + 1
                    }
                }
            }
        }
        return matches.entries
            .asSequence()
            .map { (sourceId, count) -> EmbeddingRecallHit(sourceId, count.coerceAtMost(BAND_COUNT)) }
            .sortedWith(
                compareByDescending<EmbeddingRecallHit>(EmbeddingRecallHit::bandMatches)
                    .thenBy(EmbeddingRecallHit::sourceId)
            )
            .toList()
    }

    private fun bandKey(simHash: Long, band: Int): Long = bandKey(bandValue(simHash, band), band)

    private fun bandKey(value: Int, band: Int): Long =
        (band.toLong() shl BITS_PER_BAND) or (value.toLong() and BAND_MASK)

    private fun bandValue(simHash: Long, band: Int): Int =
        ((simHash ushr (band * BITS_PER_BAND)) and BAND_MASK).toInt()

    internal companion object {
        const val BAND_COUNT = 4
        const val BITS_PER_BAND = 16
        const val MAX_POSTING_SIZE = 256
        private const val BAND_MASK = 0xFFFFL
    }
}
