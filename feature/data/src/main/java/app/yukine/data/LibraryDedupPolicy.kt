package app.yukine.data

import app.yukine.identity.LibraryDedupMode
import app.yukine.streaming.IdentityScoringMode

internal data class LibraryDedupPolicy(
    val mode: LibraryDedupMode,
    val autoMergeMinimumScore: Double,
    val autoMergeMinimumMargin: Double,
    val embeddingRecallMode: EmbeddingRecallMode,
    val scoringMode: IdentityScoringMode,
    val allowMissingDuration: Boolean
) {
    companion object {
        const val POLICY_VERSION = 1

        fun forMode(mode: LibraryDedupMode): LibraryDedupPolicy = when (mode) {
            LibraryDedupMode.SAFE -> LibraryDedupPolicy(
                mode = mode,
                autoMergeMinimumScore = 0.92,
                autoMergeMinimumMargin = 0.08,
                embeddingRecallMode = EmbeddingRecallMode.ON,
                scoringMode = IdentityScoringMode.V5_SHADOW,
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
