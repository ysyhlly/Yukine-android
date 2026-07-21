package app.yukine.streaming

/**
 * Externalized scoring configuration for [RecordingMatchEvaluatorV2].
 *
 * Encapsulates all tunable weights and thresholds that control recording identity evaluation.
 * Use the [V4] or [V5] presets for standard evaluation, or create custom configurations
 * for experimentation.
 *
 * @property scoreVersion Version identifier stamped into evaluation results for audit trails.
 * @property workWeight Weight for work identity (title + artist) in final score computation.
 * @property durationWeight Weight for duration similarity in final score computation.
 * @property versionWeight Weight for version similarity in final score computation.
 * @property softIsrcConflict When true, ISRC mismatch is a soft conflict (-penalty, capped);
 *   when false, ISRC mismatch is a hard conflict (ceiling 0.40).
 * @property artistTrigramCap Maximum score achievable via artist trigram fallback (V5 only).
 *   Prevents trigram similarity alone from reaching the auto-merge threshold.
 * @property artistTrigramHardConflictThreshold Minimum trigram score to suppress PRIMARY_ARTIST
 *   hard conflict (V5 only). Below this threshold, different artist names trigger hard conflict.
 * @property softIsrcPenalty Score penalty applied for soft ISRC conflict.
 * @property softIsrcCeiling Maximum score when soft ISRC conflict is present.
 * @property durationMissingScore Score assigned when duration is unavailable for one or both sides.
 */
data class RecordingMatchScoringConfig(
    val scoreVersion: Int,
    val workWeight: Double,
    val durationWeight: Double,
    val versionWeight: Double,
    val softIsrcConflict: Boolean,
    val artistTrigramCap: Double = 0.85,
    val artistTrigramHardConflictThreshold: Double = 0.60,
    val softIsrcPenalty: Double = 0.08,
    val softIsrcCeiling: Double = 0.91,
    val durationMissingScore: Double = 0.55,
    val useV5TitleWeighting: Boolean = false,
    val useAuthorScoreWeighting: Boolean = false
) {
    init {
        require(workWeight + durationWeight + versionWeight == 1.0) {
            "Weights must sum to 1.0: work=$workWeight, duration=$durationWeight, version=$versionWeight"
        }
        require(artistTrigramCap in 0.0..1.0) { "artistTrigramCap must be in [0, 1]" }
        require(artistTrigramHardConflictThreshold in 0.0..1.0) {
            "artistTrigramHardConflictThreshold must be in [0, 1]"
        }
        require(softIsrcPenalty in 0.0..1.0) { "softIsrcPenalty must be in [0, 1]" }
        require(softIsrcCeiling in 0.0..1.0) { "softIsrcCeiling must be in [0, 1]" }
        require(durationMissingScore in 0.0..1.0) { "durationMissingScore must be in [0, 1]" }
    }

    companion object {
        /**
         * V4 scoring profile: original production evaluator.
         * - ISRC mismatch is a hard conflict (ceiling 0.40)
         * - No artist trigram fallback (different names → 0.0 score + hard conflict)
         * - Higher duration weight (0.30) penalizes duration drift more aggressively
         */
        val V4 = RecordingMatchScoringConfig(
            scoreVersion = RecordingMatchEvaluatorV2.SCORE_VERSION,
            workWeight = 0.50,
            durationWeight = 0.30,
            versionWeight = 0.20,
            softIsrcConflict = false,
            artistTrigramCap = 0.0, // V4 does not use trigram fallback
            artistTrigramHardConflictThreshold = 1.0, // V4 always triggers hard conflict
            useV5TitleWeighting = false,
            useAuthorScoreWeighting = false
        )

        /**
         * V5 scoring profile: enhanced evaluator with spelling variant tolerance.
         * - ISRC mismatch is a soft conflict (-0.08 penalty, ceiling 0.91)
         * - Artist trigram fallback (cap 0.85) handles spelling variants without hard conflict
         * - Lower duration weight (0.15) tolerates encoding/precision differences
         * - Higher work weight (0.60) emphasizes title+artist identity
         */
        val V5 = RecordingMatchScoringConfig(
            scoreVersion = RecordingMatchEvaluatorV2.V5_SCORE_VERSION,
            workWeight = 0.60,
            durationWeight = 0.15,
            versionWeight = 0.25,
            softIsrcConflict = true,
            artistTrigramCap = 0.85,
            artistTrigramHardConflictThreshold = 0.60,
            useV5TitleWeighting = true,
            useAuthorScoreWeighting = true
        )
    }
}
