package app.yukine.data

import app.yukine.identity.LibraryDedupMode
import app.yukine.streaming.IdentityScoringMode

/**
 * Policy configuration for library deduplication.
 *
 * [autoMergeMinimumScore]: minimum group score required for automatic merge.
 * [autoMergeMinimumMargin]: minimum lead over the second-best candidate to avoid ambiguity.
 * SAFE mode requires score ≥ 0.92 AND margin ≥ 0.08; AGGRESSIVE relaxes both (0.88 / 0.04).
 * [POLICY_VERSION] is stamped into merge audit records for rollback filtering.
 */
internal data class LibraryDedupPolicy(
    val mode: LibraryDedupMode,
    val autoMergeMinimumScore: Double,
    val autoMergeMinimumMargin: Double,
    val embeddingRecallMode: EmbeddingRecallMode,
    val scoringMode: IdentityScoringMode,
    val allowMissingDuration: Boolean
) {
    companion object {
        const val POLICY_VERSION = 2

        fun forMode(mode: LibraryDedupMode): LibraryDedupPolicy = when (mode) {
            LibraryDedupMode.SAFE -> LibraryDedupPolicy(
                mode = mode,
                autoMergeMinimumScore = 0.92,
                autoMergeMinimumMargin = 0.08,
                embeddingRecallMode = EmbeddingRecallMode.ON,
                scoringMode = IdentityScoringMode.V5_ON,
                allowMissingDuration = true
            )

            LibraryDedupMode.AGGRESSIVE -> LibraryDedupPolicy(
                mode = mode,
                autoMergeMinimumScore = 0.88,
                autoMergeMinimumMargin = 0.04,
                embeddingRecallMode = EmbeddingRecallMode.ON,
                scoringMode = IdentityScoringMode.V5_ON,
                allowMissingDuration = true
            )
        }
    }
}
