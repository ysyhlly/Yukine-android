package app.yukine.streaming

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun pickBestCandidateUsesTitleMatchThenFirstFallback() {
        val local = track(title = "Needle", artist = "Missing Artist")
        val first = streamingTrack("1", "Other Song", "Other Artist")
        val titleMatch = streamingTrack("2", "Needle Extended", "Different Artist")

        assertEquals(
            titleMatch,
            StreamingTrackMatchPolicy.pickBestCandidate(local, listOf(first, titleMatch))
        )
        assertEquals(first, StreamingTrackMatchPolicy.pickBestCandidate(local, listOf(first)))
        assertNull(StreamingTrackMatchPolicy.pickBestCandidate(null, listOf(first)))
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
