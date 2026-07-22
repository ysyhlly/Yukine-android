package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimingTest {
    @Test
    fun offsetAndSeekConversionsAreInverseAndClampToZero() {
        assertEquals(1_250L, adjustedLyricPositionMs(1_000L, 250L))
        assertEquals(1_000L, playbackPositionForLyricMs(1_250L, 250L))
        assertEquals(0L, playbackPositionForLyricMs(100L, 250L))
    }

    @Test
    fun lineAndWordIntervalsAreStartInclusiveAndEndExclusive() {
        val line = LyricUiLine("line", active = false, timeMs = 1_000L, endTimeMs = 2_000L)
        val word = LyricUiWord("word", startMs = 1_200L, endMs = 1_500L)

        assertTrue(line.isActiveAt(1_000L))
        assertFalse(line.isActiveAt(2_000L))
        assertTrue(word.isActiveAt(1_200L))
        assertFalse(word.isActiveAt(1_500L))
    }

    @Test
    fun explicitOffsetsDisambiguateRepeatedWords() {
        val first = LyricUiWord("la", 0L, 100L, startOffset = 0, endOffset = 2)
        val second = LyricUiWord("la", 100L, 200L, startOffset = 3, endOffset = 5)

        assertEquals(0 to 2, first.textBounds("la la", 0))
        assertEquals(3 to 5, second.textBounds("la la", 2))
    }

    @Test
    fun wordProgressFractionAtBoundaries() {
        val word = LyricUiWord("test", startMs = 1_000L, endMs = 2_000L)

        assertEquals(0f, word.progressAt(500L), 0.001f)
        assertEquals(0f, word.progressAt(1_000L), 0.001f)
        assertEquals(0.5f, word.progressAt(1_500L), 0.001f)
        assertEquals(1f, word.progressAt(2_000L), 0.001f)
        assertEquals(1f, word.progressAt(3_000L), 0.001f)
    }

    @Test
    fun wordProgressZeroDurationWordDoesNotCrash() {
        val word = LyricUiWord("x", startMs = 1_000L, endMs = 1_000L)

        assertEquals(1f, word.progressAt(1_000L), 0.001f)
        assertEquals(1f, word.progressAt(1_500L), 0.001f)
        assertEquals(0f, word.progressAt(999L), 0.001f)
    }

    @Test
    fun wordProgressMidpointReturnsHalf() {
        val word = LyricUiWord("abcd", startMs = 0L, endMs = 400L)

        assertEquals(0.25f, word.progressAt(100L), 0.001f)
        assertEquals(0.5f, word.progressAt(200L), 0.001f)
        assertEquals(0.75f, word.progressAt(300L), 0.001f)
    }

    @Test
    fun activeLineUsesLatestStartAtOrBeforePosition() {
        val lines = listOf(
            LyricUiLine("first", false, timeMs = 1_000L),
            LyricUiLine("second", false, timeMs = 2_000L),
            LyricUiLine("third", false, timeMs = 5_000L)
        )

        assertEquals(-1, activeLyricIndex(lines, 999L))
        assertEquals(0, activeLyricIndex(lines, 1_999L))
        assertEquals(1, activeLyricIndex(lines, 4_999L))
        assertEquals(2, activeLyricIndex(lines, 8_000L))
    }

    @Test
    fun followStatePausesForUserAndResumesForSeekOrTrackChange() {
        val paused = LyricsFollowState().pauseForUserScroll()
        assertFalse(paused.following)
        assertTrue(paused.resume().following)
    }
}
