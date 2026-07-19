package app.yukine.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataHashEmbeddingEncoderTest {
    @Test
    fun encodingIsDeterministicForUnicodeMetadata() {
        val first = encode(title = "星空物語", artist = "测试歌手", year = 2021)
        val second = encode(title = "星空物語", artist = "测试歌手", year = 2021)

        assertEquals(MetadataHashEmbeddingEncoder.DIMENSIONS, first.vector.size)
        assertArrayEquals(first.vector, second.vector)
        assertEquals(first.simHash, second.simHash)
        assertEquals(1.0, MetadataHashEmbeddingEncoder.cosine(first.vector, second.vector), 0.0001)
    }

    @Test
    fun fieldPrefixesAndYearBucketsChangeTheVector() {
        val original = encode(title = "Home", artist = "Artist", year = 2020)
        val changedArtist = encode(title = "Home", artist = "Home", year = 2020)
        val changedBucket = encode(title = "Home", artist = "Artist", year = 2025)

        assertNotEquals(original.vector.toList(), changedArtist.vector.toList())
        assertNotEquals(original.vector.toList(), changedBucket.vector.toList())
    }

    @Test
    fun emptyMetadataProducesNoEmbedding() {
        assertNull(
            MetadataHashEmbeddingEncoder.encode(
                title = "",
                artist = "",
                album = "",
                albumArtist = "",
                composer = "",
                versionSignature = "",
                releaseType = "",
                year = 0
            )
        )
    }

    @Test
    fun lshUsesSingleBitProbeAndSkipsPathologicalBuckets() {
        val index = EmbeddingPostingIndex(
            listOf(
                EmbeddingIndexItem(1L, 0L),
                EmbeddingIndexItem(2L, 1L),
                EmbeddingIndexItem(3L, -1L)
            )
        )

        val hits = index.recall(0L, 1L)
        assertTrue(hits.any { it.sourceId == 2L })

        val pathological = EmbeddingPostingIndex(
            (1L..257L).map { sourceId -> EmbeddingIndexItem(sourceId, 0L) }
        )
        assertTrue(pathological.recall(0L, 999L).isEmpty())
    }

    private fun encode(title: String, artist: String, year: Int): MetadataEmbedding =
        checkNotNull(
            MetadataHashEmbeddingEncoder.encode(
                title = title,
                artist = artist,
                album = "Album",
                albumArtist = artist,
                composer = "",
                versionSignature = "ORIGINAL",
                releaseType = "album",
                year = year
            )
        )
}
