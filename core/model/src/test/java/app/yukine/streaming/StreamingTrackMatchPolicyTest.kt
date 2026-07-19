package app.yukine.streaming

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTrackMatchPolicyTest {
    @Test
    fun searchQueryUsesTitleAndArtistAndSkipsUnknownValues() {
        assertEquals(
            "Song Artist",
            StreamingTrackMatchPolicy.searchQuery(track(title = "Song", artist = "Artist"))
        )
        assertEquals(
            "Artist",
            StreamingTrackMatchPolicy.searchQuery(track(title = "\u672a\u77e5\u6b4c\u66f2", artist = "Artist"))
        )
        assertEquals("", StreamingTrackMatchPolicy.searchQuery(null))
    }

    @Test
    fun fullMetadataSearchQueryUsesTitleArtistAndAlbum() {
        assertEquals(
            "Song Artist Album",
            StreamingTrackMatchPolicy.fullMetadataSearchQuery(
                track(title = " Song ", artist = "Artist")
            )
        )
        assertEquals(
            "Artist Album",
            StreamingTrackMatchPolicy.fullMetadataSearchQuery(
                track(title = "\u672a\u77e5\u6b4c\u66f2", artist = "Artist")
            )
        )
        assertEquals("", StreamingTrackMatchPolicy.fullMetadataSearchQuery(null))
    }

    @Test
    fun pickBestCandidatePrefersExactSanitizedTitleAndArtist() {
        val local = track(title = "Local Song", artist = "Local Artist")
        val fallback = streamingTrack("1", "Other Song", "Other Artist")
        val exact = streamingTrack("2", " Local Song ", " Local Artist ")

        assertEquals(
            exact,
            StreamingTrackMatchPolicy.pickBestCandidate(local, listOf(fallback, exact))
        )
    }

    @Test
    fun pickBestCandidateDoesNotPreferTitleMatchAcrossArtistConflict() {
        val local = track(title = "Needle", artist = "Missing Artist")
        val first = streamingTrack("1", "Other Song", "Other Artist")
        val titleMatch = streamingTrack("2", "Needle Theme", "Different Artist")

        assertEquals(
            first,
            StreamingTrackMatchPolicy.pickBestCandidate(local, listOf(first, titleMatch))
        )
        assertEquals(first, StreamingTrackMatchPolicy.pickBestCandidate(local, listOf(first)))
        assertNull(StreamingTrackMatchPolicy.pickBestCandidate(null, listOf(first)))
    }

    @Test
    fun reliableMatchHandlesTranslatedTitleArtistOrderAndDurationDrift() {
        val local = track(
            title = "10年後の私になら (如果是10年后的我)",
            artist = "こはならむ"
        )
        val candidate = streamingTrack(
            id = "cross-source",
            title = "10年後の私になら",
            artist = "こはならむ (Kohana Lam)"
        ).copy(durationMs = 183_500L)

        val match = StreamingTrackMatchPolicy.rankCandidates(
            StreamingTrackMatchPolicy.reference(local),
            listOf(candidate)
        ).single()

        assertTrue(match.reliable)
        assertEquals(candidate, StreamingTrackMatchPolicy.pickReliableCandidate(local, listOf(candidate)))
    }

    @Test
    fun isrcDoesNotOverrideArtistConflictAndBadVersionsDoNotReliablyMerge() {
        val local = track(title = "Echo", artist = "Artist")
        val wrongVersion = streamingTrack("live", "Echo (Live)", "Artist").copy(durationMs = 180_000L)
        val tooLong = streamingTrack("long", "Echo", "Artist").copy(durationMs = 205_000L)
        val isrc = streamingTrack("isrc", "完全不同的标题", "其他歌手").copy(isrc = "JP-ABC-12-34567")
        val ranked = StreamingTrackMatchPolicy.rankCandidates(
            StreamingTrackMatchPolicy.Reference(
                title = local.title,
                artist = local.artist,
                album = local.album,
                durationMs = local.durationMs,
                isrc = "JPABC1234567"
            ),
            listOf(wrongVersion, tooLong, isrc)
        )

        val conflictingIsrc = ranked.first { it.track == isrc }
        assertFalse(ranked.first().track.providerTrackId == "isrc")
        assertTrue(conflictingIsrc.isrcExact)
        assertTrue(conflictingIsrc.evaluation.hasHardConflict)
        assertFalse(ranked.first { it.track == wrongVersion }.reliable)
        assertFalse(ranked.first { it.track == tooLong }.reliable)
    }

    private fun track(
        title: String,
        artist: String,
        id: Long = 1L
    ): Track =
        Track(
            id,
            title,
            artist,
            "Album",
            180_000L,
            Uri.EMPTY,
            "local:$id"
        )

    private fun streamingTrack(
        id: String,
        title: String,
        artist: String
    ): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = title,
            artist = artist
        )
}
