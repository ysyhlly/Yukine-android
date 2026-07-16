package app.yukine.data

import app.yukine.data.room.ArtistCreditParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistCreditParserTest {
    @Test
    fun normalizationUsesNfkcCaseFoldingAndStableWhitespace() {
        assertEquals("hanser", ArtistCreditParser.normalizeAlias("  Ｈａｎｓｅｒ  "))
        assertEquals("初音ミク", ArtistCreditParser.normalizeAlias("初音ミク"))
    }

    @Test
    fun featureMarkersCreatePrimaryAndFeaturedCredits() {
        val values = ArtistCreditParser.parse("A feat. B")

        assertEquals(listOf("A", "B"), values.map { it.name })
        assertEquals(listOf("PRIMARY", "FEATURED"), values.map { it.role })
        assertEquals(listOf(0, 1), values.map { it.position })
    }

    @Test
    fun commonParallelSeparatorsSplitButAmpersandStaysIntact() {
        assertEquals(
            listOf("A", "B", "C", "D"),
            ArtistCreditParser.parse("A / B、C and D").map { it.name }
        )
        assertEquals(
            listOf("Simon & Garfunkel"),
            ArtistCreditParser.parse("Simon & Garfunkel").map { it.name }
        )
    }

    @Test
    fun unknownAndVariousArtistsRemainUnconfirmedSingleCredits() {
        listOf("Various Artists", "未知艺人", "").forEach { raw ->
            val credit = ArtistCreditParser.parse(raw).single()
            assertEquals("UNKNOWN", credit.role)
        }
    }
}
