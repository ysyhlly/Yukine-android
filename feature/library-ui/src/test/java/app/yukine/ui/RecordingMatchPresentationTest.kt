package app.yukine.ui

import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityTargetType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingMatchPresentationTest {
    @Test
    fun candidatePresentationShowsProviderEvidenceAndHardConflict() {
        val candidate = IdentityCandidate(
            candidateId = "candidate-1",
            targetType = IdentityTargetType.RECORDING,
            targetId = 42L,
            provider = "netease",
            providerItemId = "wy-42",
            title = "Candidate title",
            artist = "Candidate artist",
            album = "Candidate album",
            durationMs = 180_000L,
            isrc = "USAAA1234567",
            variantType = "LIVE",
            score = 0.934,
            status = IdentityCandidateStatus.PENDING,
            evidenceJson = """{"reasons":["title:exact","artist:conflict"],"hardConflict":true,"margin":0.12}"""
        )

        val label = RecordingMatchPresentation.candidateLabel("en", candidate)
        val details = RecordingMatchPresentation.candidateDetails("en", candidate)

        assertTrue(label.contains("NetEase Cloud Music"))
        assertTrue(label.contains("Candidate title"))
        assertTrue(details.contains("Candidate album"))
        assertTrue(details.contains("3:00"))
        assertTrue(details.contains("ISRC: USAAA1234567"))
        assertTrue(details.contains("Live"))
        assertTrue(details.contains("title:exact"))
        assertTrue(details.contains("hardConflict: true"))
        assertTrue(RecordingMatchPresentation.hasHardConflict(candidate))
    }

    @Test
    fun candidatePresentationShowsAllSafeEvidenceDimensionsAndRedactsSecrets() {
        val candidate = IdentityCandidate(
            candidateId = "candidate-evidence",
            targetType = IdentityTargetType.RECORDING,
            targetId = 43L,
            provider = "qqmusic",
            providerItemId = "qq-43",
            title = "Candidate",
            artist = "Artist",
            score = 0.8,
            status = IdentityCandidateStatus.PENDING,
            evidenceJson = """{"titleScore":0.95,"artistScore":0.88,"durationDeltaMs":1200,"apiUrl":"https://secret.test","token":"abc"}"""
        )

        val details = RecordingMatchPresentation.candidateDetails("en", candidate)

        assertTrue(details.contains("titleScore: 0.95"))
        assertTrue(details.contains("artistScore: 0.88"))
        assertTrue(details.contains("durationDeltaMs: 1200"))
        assertTrue(details.contains("apiUrl: [redacted]"))
        assertTrue(details.contains("token: [redacted]"))
        assertFalse(details.contains("secret.test"))
        assertFalse(details.contains("\"abc\""))
    }
}
