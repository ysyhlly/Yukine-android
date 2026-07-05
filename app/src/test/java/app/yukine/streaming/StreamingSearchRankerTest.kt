package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSearchRankerTest {
    @Test
    fun levenshteinDistanceClassic() {
        assertEquals(3, levenshteinDistance("kitten", "sitting"))
    }

    @Test
    fun levenshteinDistanceEmptyOperand() {
        assertEquals(0, levenshteinDistance("", ""))
        assertEquals(3, levenshteinDistance("abc", ""))
        assertEquals(3, levenshteinDistance("", "abc"))
    }

    @Test
    fun levenshteinDistanceSymmetric() {
        assertEquals(levenshteinDistance("abc", "xyz"), levenshteinDistance("xyz", "abc"))
    }

    @Test
    fun similarityScoreOrderedExactPrefixContains() {
        val query = "hello"
        assertEquals(1_000, similarityScore(query, "hello"))
        assertEquals(850, similarityScore(query, "hello world"))
        assertEquals(720, similarityScore(query, "say hello now"))
        assertEquals(0, similarityScore("", "hello"))
        assertEquals(0, similarityScore("hello", ""))
    }

    @Test
    fun rankTextNormalizesCasePunctuationAndWhitespace() {
        assertEquals("a b", "A & B".rankText())
        assertEquals("hello", "Hello!!".rankText())
        assertEquals("a b c", "a  b  c".rankText())
        assertEquals("", "".rankText())
    }

    @Test
    fun rankBySearchSimilarityIsStableOnTies() {
        val tracks = listOf(
            track("t1", "Alpha"),
            track("t2", "Beta"),
            track("t3", "Alpha")
        )
        val ranked = tracks.rankBySearchSimilarity("alpha")
        assertEquals("t1", ranked[0].providerTrackId)
        assertEquals("t3", ranked[1].providerTrackId)
        assertEquals("t2", ranked[2].providerTrackId)
    }

    @Test
    fun rankBySearchSimilarityBlankQueryReturnsOriginalOrder() {
        val tracks = listOf(track("t1", "Alpha"), track("t2", "Beta"))
        assertEquals(tracks, tracks.rankBySearchSimilarity("   "))
    }

    @Test
    fun rankBySearchSimilarityRanksTitleMatchFirst() {
        val tracks = listOf(
            track("t1", "Beta Song"),
            track("t2", "Alpha Song")
        )
        val ranked = tracks.rankBySearchSimilarity("alpha")
        assertEquals("t2", ranked[0].providerTrackId)
    }

    private fun track(id: String, title: String, artist: String = "Artist"): StreamingTrack {
        return StreamingTrack(
            provider = StreamingProviderName.MOCK,
            providerTrackId = id,
            title = title,
            artist = artist
        )
    }
}
