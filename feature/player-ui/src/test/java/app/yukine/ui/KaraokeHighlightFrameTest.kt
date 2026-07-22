package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeHighlightFrameTest {
    @Test
    fun frameSeparatesCompletedCurrentAndUpcomingWords() {
        val frame = karaokeHighlightFrame(
            text = "one two three",
            words = listOf(
                KaraokeWordTiming("one", 0L, 100L, 0, 3),
                KaraokeWordTiming("two", 100L, 300L, 4, 7),
                KaraokeWordTiming("three", 300L, 500L, 8, 13)
            ),
            positionMs = 200L
        )

        assertEquals(KaraokeHighlightPhase.COMPLETED, frame.ranges[0].phase)
        assertEquals(KaraokeHighlightPhase.CURRENT, frame.ranges[1].phase)
        assertEquals(0.5f, frame.ranges[1].progress, 0.001f)
        assertEquals(KaraokeHighlightPhase.UPCOMING, frame.ranges[2].phase)
    }

    @Test
    fun explicitOffsetsAndSequentialFallbackHandleRepeatedWords() {
        val frame = karaokeHighlightFrame(
            text = "la la",
            words = listOf(
                KaraokeWordTiming("la", 0L, 100L, 0, 2),
                KaraokeWordTiming("la", 100L, 200L)
            ),
            positionMs = 50L
        )

        assertEquals(listOf(0 to 2, 3 to 5), frame.ranges.map { it.start to it.end })
    }

    @Test
    fun malformedWordsAreSkippedWithoutDroppingValidUnicodeWords() {
        val frame = karaokeHighlightFrame(
            text = "你好 שלום",
            words = listOf(
                KaraokeWordTiming("missing", 0L, 100L, 20, 30),
                KaraokeWordTiming("你好", 0L, 100L, 0, 2),
                KaraokeWordTiming("שלום", 100L, 200L, 3, 7)
            ),
            positionMs = 150L
        )

        assertEquals(listOf("你好", "שלום"), frame.ranges.map { frame.text.substring(it.start, it.end) })
        assertTrue(frame.ranges.last().progress in 0.49f..0.51f)
    }

    @Test
    fun zeroDurationWordCompletesAtItsStart() {
        val frame = karaokeHighlightFrame(
            text = "x",
            words = listOf(KaraokeWordTiming("x", 1_000L, 1_000L, 0, 1)),
            positionMs = 1_000L
        )

        assertEquals(KaraokeHighlightPhase.COMPLETED, frame.ranges.single().phase)
        assertEquals(1f, frame.ranges.single().progress, 0.001f)
    }

    @Test
    fun negativeDurationWordIsSkippedAndDoesNotConsumeRepeatedText() {
        val frame = karaokeHighlightFrame(
            text = "la la",
            words = listOf(
                KaraokeWordTiming("la", 200L, 100L, 0, 2),
                KaraokeWordTiming("la", 300L, 400L)
            ),
            positionMs = 250L
        )

        assertEquals(1, frame.ranges.size)
        assertEquals(0 to 2, frame.ranges.single().let { it.start to it.end })
        assertEquals(KaraokeHighlightPhase.UPCOMING, frame.ranges.single().phase)
    }
}
