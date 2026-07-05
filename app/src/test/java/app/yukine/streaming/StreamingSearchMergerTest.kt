package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSearchMergerTest {
    @Test
    fun crossSourceMergeKeyIndependentOfArtistTokenOrder() {
        val left = track("1", "Song", "Bob Alice")
        val right = track("2", "Song", "Alice Bob")
        assertEquals(left.crossSourceMergeKey(), right.crossSourceMergeKey())
    }

    @Test
    fun crossSourceMergeKeyDiffersForDifferentTitles() {
        val left = track("1", "Alpha", "Artist")
        val right = track("2", "Beta", "Artist")
        assertNotEquals(left.crossSourceMergeKey(), right.crossSourceMergeKey())
    }

    @Test
    fun isSameSongAcrossSourceWithinDurationTolerance() {
        val left = track("1", "Song", "Artist", durationMs = 200_000L)
        val right = track("2", "Song", "Artist", durationMs = 201_000L)
        assertTrue(left.isSameSongAcrossSource(right))
    }

    @Test
    fun isSameSongAcrossSourceOutsideDurationTolerance() {
        val left = track("1", "Song", "Artist", durationMs = 200_000L)
        val right = track("2", "Song", "Artist", durationMs = 210_000L)
        assertEquals(false, left.isSameSongAcrossSource(right))
    }

    @Test
    fun isSameSongAcrossSourceNullDurationTreatedAsSame() {
        val left = track("1", "Song", "Artist", durationMs = null)
        val right = track("2", "Song", "Artist", durationMs = null)
        assertTrue(left.isSameSongAcrossSource(right))
    }

    @Test
    fun mergeSourcesIntoRepresentativePreservesExistingCandidatesAndFoldsOthers() {
        val existing = StreamingPlaybackCandidate(
            provider = StreamingProviderName.MOCK,
            providerTrackId = "1",
            available = true
        )
        val rep = track("1", "Song", "Artist", playable = true)
            .copy(playbackCandidates = listOf(existing))
        val other = track("2", "Song", "Artist", playable = false, provider = StreamingProviderName.LUOXUE)
        val merged = listOf(rep, other).mergeSourcesIntoRepresentative()
        assertEquals("1", merged.providerTrackId)
        assertEquals(2, merged.playbackCandidates.size)
    }

    @Test
    fun mergeCrossSourceDuplicatesMergesSameSongAcrossProviders() {
        val t1 = track("1", "Song", "Artist", durationMs = 200_000L, provider = StreamingProviderName.MOCK)
        val t2 = track("2", "Song", "Artist", durationMs = 200_000L, provider = StreamingProviderName.LUOXUE)
        val result = StreamingSearchResult(
            provider = StreamingProviderName.MOCK,
            query = "song",
            page = 1,
            pageSize = 20,
            tracks = listOf(t1, t2)
        ).mergeCrossSourceDuplicates()
        assertEquals(1, result.tracks.size)
    }

    @Test
    fun mergeCrossSourceDuplicatesKeepsDifferentSongs() {
        val t1 = track("1", "Alpha", "Artist", durationMs = 200_000L)
        val t2 = track("2", "Beta", "Artist", durationMs = 200_000L)
        val result = StreamingSearchResult(
            provider = StreamingProviderName.MOCK,
            query = "song",
            page = 1,
            pageSize = 20,
            tracks = listOf(t1, t2)
        ).mergeCrossSourceDuplicates()
        assertEquals(2, result.tracks.size)
    }

    @Test
    fun trackOnlySearchResultFiltersNonTrackItems() {
        val song = track("1", "Song", "Artist")
        val trackItem = StreamingSearchItem.fromTrack(song)
        val albumItem = StreamingSearchItem.fromAlbum(
            StreamingAlbum(
                provider = StreamingProviderName.MOCK,
                providerAlbumId = "a1",
                title = "Album",
                artist = "Artist"
            )
        )
        val result = StreamingSearchResult(
            provider = StreamingProviderName.MOCK,
            query = "song",
            page = 1,
            pageSize = 20,
            tracks = listOf(song),
            items = listOf(trackItem, albumItem)
        ).trackOnlySearchResult()
        assertEquals(1, result.tracks.size)
        assertEquals(0, result.albums.size)
    }

    @Test
    fun mergeStreamingSearchResultsDeduplicatesTracksByProviderAndTrackId() {
        val t1 = track("1", "Song", "Artist", provider = StreamingProviderName.MOCK)
        val t2 = track("1", "Song", "Artist", provider = StreamingProviderName.MOCK)
        val r1 = StreamingSearchResult(StreamingProviderName.MOCK, "q", 1, 20, total = 1, tracks = listOf(t1))
        val r2 = StreamingSearchResult(StreamingProviderName.MOCK, "q", 1, 20, total = 1, tracks = listOf(t2))
        val merged = mergeStreamingSearchResults("song", 20, listOf(r1, r2))
        assertEquals(1, merged.tracks.size)
    }

    @Test
    fun mergeStreamingSearchResultsSumsTotalsAcrossProviders() {
        val r1 = StreamingSearchResult(StreamingProviderName.MOCK, "q", 1, 20, total = 10, tracks = listOf(track("1", "A", "X")))
        val r2 = StreamingSearchResult(
            StreamingProviderName.LUOXUE, "q", 1, 20, total = 20,
            tracks = listOf(track("2", "B", "Y", provider = StreamingProviderName.LUOXUE))
        )
        val merged = mergeStreamingSearchResults("q", 20, listOf(r1, r2))
        assertEquals(30, merged.total)
    }

    @Test
    fun mergeStreamingSearchResultsPicksFirstProviderWithTracks() {
        val r1 = StreamingSearchResult(StreamingProviderName.MOCK, "q", 1, 20, total = 0, tracks = emptyList())
        val r2 = StreamingSearchResult(
            StreamingProviderName.LUOXUE, "q", 1, 20, total = 1,
            tracks = listOf(track("2", "B", "Y", provider = StreamingProviderName.LUOXUE))
        )
        val merged = mergeStreamingSearchResults("q", 20, listOf(r1, r2))
        assertEquals(StreamingProviderName.LUOXUE, merged.provider)
    }

    private fun track(
        id: String,
        title: String,
        artist: String,
        durationMs: Long? = null,
        playable: Boolean = true,
        provider: StreamingProviderName = StreamingProviderName.MOCK
    ): StreamingTrack {
        return StreamingTrack(
            provider = provider,
            providerTrackId = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            playable = playable
        )
    }
}
