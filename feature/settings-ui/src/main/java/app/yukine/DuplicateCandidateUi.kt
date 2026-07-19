package app.yukine

data class DuplicateCandidateCenterUi(
    val total: Int = 0,
    val reviewRequired: Int = 0,
    val items: List<DuplicateCandidateUi> = emptyList()
) {
    val highConfidenceCount: Int get() = items.count(DuplicateCandidateUi::batchEligible)
}

data class DuplicateCandidateUi(
    val leftRecordingId: Long,
    val rightRecordingId: Long,
    val leftLabel: String,
    val rightLabel: String,
    val score: Double,
    val margin: Double,
    val relationType: String,
    val batchEligible: Boolean
)
