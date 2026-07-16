package app.yukine.fingerprint

import app.yukine.model.Track

enum class AudioAnalysisMode {
    FAST,
    BALANCED,
    ENHANCED,
    AI_DEEP
}

enum class AudioAnalysisKind {
    QUICK,
    FULL,
    SEGMENTED
}

data class FingerprintCandidateTrack(
    val trackId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isrc: String = "",
    val recordingMbid: String = "",
    val provider: String = "",
    val providerTrackId: String = "",
    val locallyReadable: Boolean = true,
    val podcastOrAudiobook: Boolean = false
)

data class FingerprintCandidatePair(
    val firstTrackId: Long,
    val secondTrackId: Long,
    val reasons: Set<String>
)

/** Cold-path work item. [contentSignature] is an optimistic-lock token for Room writes. */
data class AudioFingerprintCandidate(
    val sourceId: Long,
    val track: Track,
    val contentSignature: String,
    val previousAlgorithmVersion: Int = 0
)

/** Versioned traditional audio evidence produced without entering playback or library hot paths. */
data class AudioFingerprintEvidence(
    val pcmHash: String = "",
    val chromaprint: String,
    val algorithmVersion: Int,
    val analyzedDurationMs: Long
)
