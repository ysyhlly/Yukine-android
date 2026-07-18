package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCardDensityTest {
    @Test
    fun compactModeKeepsExistingLibraryDimensions() {
        val compact = libraryCardDensityTokens(compact = true)

        assertEquals(88f, compact.trackRowHeight.value, 0f)
        assertEquals(64f, compact.groupRowMinHeight.value, 0f)
        assertEquals(54f, compact.personalRowHeight.value, 0f)
    }

    @Test
    fun nonCompactModeUsesIndependentCardsWithoutInflatingContent() {
        val compact = libraryCardDensityTokens(compact = true)
        val independent = libraryCardDensityTokens(compact = false)

        assertFalse(compact.independentCards)
        assertTrue(independent.independentCards)
        assertEquals(compact.sectionSpacing, independent.sectionSpacing)
        assertEquals(compact.browseCellHeight, independent.browseCellHeight)
        assertEquals(compact.groupRowMinHeight, independent.groupRowMinHeight)
        assertEquals(compact.artistArtworkSize, independent.artistArtworkSize)
        assertEquals(compact.trackRowHeight, independent.trackRowHeight)
        assertEquals(compact.trackArtworkSize, independent.trackArtworkSize)
    }
}
