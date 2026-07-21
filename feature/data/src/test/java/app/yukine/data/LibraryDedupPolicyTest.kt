package app.yukine.data

import app.yukine.identity.LibraryDedupMode
import app.yukine.streaming.IdentityScoringMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDedupPolicyTest {
    @Test
    fun missingOrInvalidStoredModeFallsBackToSafe() {
        assertEquals(LibraryDedupMode.SAFE, LibraryDedupMode.fromStoredValue(null))
        assertEquals(LibraryDedupMode.SAFE, LibraryDedupMode.fromStoredValue(""))
        assertEquals(LibraryDedupMode.SAFE, LibraryDedupMode.fromStoredValue("unknown"))
        assertEquals(LibraryDedupMode.SAFE, LibraryDedupMode.fromStoredValue(" safe "))
        assertEquals(LibraryDedupMode.AGGRESSIVE, LibraryDedupMode.fromStoredValue("aggressive"))
    }

    @Test
    fun safePolicyKeepsStrictThresholdsAndMissingDurationGate() {
        val policy = LibraryDedupPolicy.forMode(LibraryDedupMode.SAFE)

        assertEquals(0.92, policy.autoMergeMinimumScore, 0.0)
        assertEquals(0.08, policy.autoMergeMinimumMargin, 0.0)
        assertEquals(EmbeddingRecallMode.ON, policy.embeddingRecallMode)
        assertEquals(IdentityScoringMode.V5_ON, policy.scoringMode)
        assertTrue(policy.allowMissingDuration)
    }

    @Test
    fun aggressivePolicyEnablesEnhancedRecallWithoutChangingPolicyVersion() {
        val policy = LibraryDedupPolicy.forMode(LibraryDedupMode.AGGRESSIVE)

        assertEquals(0.88, policy.autoMergeMinimumScore, 0.0)
        assertEquals(0.04, policy.autoMergeMinimumMargin, 0.0)
        assertEquals(EmbeddingRecallMode.ON, policy.embeddingRecallMode)
        assertEquals(IdentityScoringMode.V5_ON, policy.scoringMode)
        assertTrue(policy.allowMissingDuration)
        assertEquals(2, LibraryDedupPolicy.POLICY_VERSION)
    }
}
