package app.yukine.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintCandidateBucketBuilderTest {
    @Test
    fun recallsByStrongIdsAndNormalizedMetadata() {
        val pairs = FingerprintCandidateBucketBuilder().build(listOf(
            track(1, "Song (feat. Guest)", "Artist", 180_000, isrc = "CN-A01-24-00001"),
            track(2, "song", "ARTIST", 181_500, isrc = "cn-a01-24-00001"),
            track(3, "Other", "Else", 220_000)
        ))

        assertEquals(1, pairs.size)
        assertEquals(1L, pairs.single().firstTrackId)
        assertEquals(2L, pairs.single().secondTrackId)
        assertTrue(FingerprintCandidateBucketBuilder.REASON_ISRC in pairs.single().reasons)
        assertTrue(FingerprintCandidateBucketBuilder.REASON_TITLE_ARTIST in pairs.single().reasons)
    }

    @Test
    fun excludesRemoteOnlyAndSpokenContent() {
        val pairs = FingerprintCandidateBucketBuilder().build(listOf(
            track(1, "Same", "Artist", 180_000),
            track(2, "Same", "Artist", 180_000, locallyReadable = false),
            track(3, "Same", "Artist", 180_000, podcastOrAudiobook = true)
        ))

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun capsDenseBucketsWithoutQuadraticExplosion() {
        val pairs = FingerprintCandidateBucketBuilder(maxCandidatesPerTrack = 4).build(
            (1L..100L).map { track(it, "Same", "Artist", 180_000) }
        )
        val counts = HashMap<Long, Int>()
        pairs.forEach { pair ->
            counts[pair.firstTrackId] = counts.getOrDefault(pair.firstTrackId, 0) + 1
            counts[pair.secondTrackId] = counts.getOrDefault(pair.secondTrackId, 0) + 1
        }

        assertTrue(pairs.size <= 200)
        assertTrue(counts.values.all { it <= 4 })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        durationMs: Long,
        isrc: String = "",
        locallyReadable: Boolean = true,
        podcastOrAudiobook: Boolean = false
    ) = FingerprintCandidateTrack(
        trackId = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        isrc = isrc,
        locallyReadable = locallyReadable,
        podcastOrAudiobook = podcastOrAudiobook
    )
}
