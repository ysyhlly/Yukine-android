package app.yukine.data

import app.yukine.fingerprint.TraditionalAudioEvidence

internal data class ChromaprintBucketHit(
    val sourceId: Long,
    val bucketMatches: Int
)

/**
 * Small in-memory cold-path index over already persisted Chromaprint segments.
 *
 * Each segment is reduced to a 32-bit bit-majority signature and split into four
 * eight-bit buckets. Matching any bucket provides recall only; the existing
 * segment aligner remains the authority for audio evidence during scoring.
 */
internal class ChromaprintBucketIndex(
    evidenceBySourceId: Map<Long, TraditionalAudioEvidence>
) {
    private val keysBySourceId = evidenceBySourceId.mapValues { (_, evidence) ->
        bucketKeys(evidence)
    }
    private val postings = HashMap<Int, MutableList<Long>>()

    init {
        keysBySourceId.entries.sortedBy(Map.Entry<Long, Set<Int>>::key).forEach { (sourceId, keys) ->
            keys.forEach { key ->
                postings.getOrPut(key) { ArrayList() } += sourceId
            }
        }
    }

    fun recall(sourceId: Long): List<ChromaprintBucketHit> {
        val matches = HashMap<Long, Int>()
        keysBySourceId[sourceId].orEmpty().forEach { key ->
            val rows = postings[key].orEmpty()
            if (rows.size > MAX_POSTING_SIZE) return@forEach
            rows.forEach { candidateSourceId ->
                if (candidateSourceId != sourceId) {
                    matches[candidateSourceId] = (matches[candidateSourceId] ?: 0) + 1
                }
            }
        }
        return matches.entries
            .map { (candidateSourceId, count) ->
                ChromaprintBucketHit(candidateSourceId, count)
            }
            .sortedWith(
                compareByDescending<ChromaprintBucketHit>(ChromaprintBucketHit::bucketMatches)
                    .thenBy(ChromaprintBucketHit::sourceId)
            )
    }

    internal companion object {
        private const val BAND_COUNT = 4
        private const val BITS_PER_BAND = 8
        private const val BAND_MASK = 0xFF
        private const val MAX_POSTING_SIZE = 256

        fun bucketKeys(evidence: TraditionalAudioEvidence): Set<Int> = buildSet {
            evidence.segments.forEach { segment ->
                if (segment.words.isEmpty()) return@forEach
                val signature = bitMajoritySignature(segment.words)
                repeat(BAND_COUNT) { band ->
                    val value = (signature ushr (band * BITS_PER_BAND)) and BAND_MASK
                    add((band shl BITS_PER_BAND) or value)
                }
            }
        }

        private fun bitMajoritySignature(words: IntArray): Int {
            var signature = 0
            repeat(Int.SIZE_BITS) { bit ->
                var balance = 0
                words.forEach { word ->
                    balance += if ((word ushr bit) and 1 == 1) 1 else -1
                }
                if (balance >= 0) signature = signature or (1 shl bit)
            }
            return signature
        }
    }
}
