package app.yukine.streaming

enum class IdentityScoringMode {
    V4_ONLY,
    V5_SHADOW,
    V5_ON;

    companion object {
        @JvmStatic
        fun fromStoredValue(value: String?): IdentityScoringMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: V5_SHADOW
    }
}

data class RecordingMatchEvaluationDecision(
    val active: MatchEvaluation,
    val shadow: MatchEvaluation? = null
)

/** Pure version selector. Persistence owns the mode; the evaluator owns no mutable state. */
object RecordingMatchEvaluationPolicy {
    @JvmStatic
    fun evaluate(
        reference: StreamingTrackMatchPolicy.Reference,
        candidate: StreamingTrackMatchPolicy.Reference,
        mode: IdentityScoringMode,
        includeExplanation: Boolean = true,
        recallEvidence: MetadataRecallEvidence? = null
    ): RecordingMatchEvaluationDecision = evaluate(
        RecordingMatchFeatureExtractor.extract(reference),
        RecordingMatchFeatureExtractor.extract(candidate),
        mode,
        includeExplanation,
        recallEvidence
    )

    @JvmStatic
    fun evaluate(
        reference: RecordingMatchFeatures,
        candidate: RecordingMatchFeatures,
        mode: IdentityScoringMode,
        includeExplanation: Boolean = true,
        recallEvidence: MetadataRecallEvidence? = null
    ): RecordingMatchEvaluationDecision {
        val v4 = RecordingMatchEvaluatorV2.evaluate(
            reference,
            candidate,
            includeExplanation,
            recallEvidence
        )
        return when (mode) {
            IdentityScoringMode.V4_ONLY -> RecordingMatchEvaluationDecision(active = v4)
            IdentityScoringMode.V5_SHADOW -> RecordingMatchEvaluationDecision(
                active = v4,
                shadow = RecordingMatchEvaluatorV2.evaluateV5(
                    reference,
                    candidate,
                    includeExplanation,
                    recallEvidence
                )
            )
            IdentityScoringMode.V5_ON -> RecordingMatchEvaluationDecision(
                active = RecordingMatchEvaluatorV2.evaluateV5(
                    reference,
                    candidate,
                    includeExplanation,
                    recallEvidence
                ),
                shadow = v4
            )
        }
    }
}
