package app.yukine.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackIdentityTest {
    @Test
    fun stableNegativeIdsAreUsableButTheSentinelIsNot() {
        assertTrue(TrackIdentity.isUsable(-42L))
        assertTrue(TrackIdentity.isUsable(42L))
        assertFalse(TrackIdentity.isUsable(TrackIdentity.INVALID_ID))
        assertEquals(-2L, TrackIdentity.stableNegative(-1L))
    }
}
