package app.yukine.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class TrackIdentityTest {
    @Test
    fun stableNegativeIdsAreUsableButTheSentinelIsNot() {
        assertTrue(TrackIdentity.isUsable(-42L))
        assertTrue(TrackIdentity.isUsable(42L))
        assertFalse(TrackIdentity.isUsable(TrackIdentity.INVALID_ID))
        assertEquals(-2L, TrackIdentity.stableNegative(-1L))
    }

    @Test
    fun embeddedIdentityTagsNormalizeStrongIdentifiersAndDropMalformedValues() {
        val tags = TrackIdentityTags(
            "123E4567-E89B-12D3-A456-426614174000",
            "not-a-uuid",
            "JP-ABC-12-34567",
            "123e4567-e89b-12d3-a456-426614174002",
            listOf(
                "123e4567-e89b-12d3-a456-426614174003",
                "bad",
                "123e4567-e89b-12d3-a456-426614174003"
            )
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174000", tags.recordingMusicBrainzId)
        assertEquals("", tags.workMusicBrainzId)
        assertEquals("JPABC1234567", tags.isrc)
        assertEquals(1, tags.artistMusicBrainzIds.size)
        assertEquals(tags.recordingMusicBrainzId, UUID.fromString(tags.recordingMusicBrainzId).toString())
    }
}
