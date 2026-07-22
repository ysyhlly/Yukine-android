package app.yukine

import app.yukine.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingLyricsPublisherTest {
    private val lines = listOf(
        LyricLine(1_000L, 1_999L, "first"),
        LyricLine(2_000L, 2_999L, "second")
    )

    @Test
    fun textAtOrBeforeDoesNotShowFutureLine() {
        assertEquals("first", FloatingLyricsPublisher.textAtOrBefore(lines, 1_900L))
    }

    @Test
    fun textAtOrBeforeReturnsEmptyBeforeFirstLine() {
        assertEquals("", FloatingLyricsPublisher.textAtOrBefore(lines, 999L))
    }

    @Test
    fun textAtOrBeforeSwitchesAtExactStart() {
        assertEquals("second", FloatingLyricsPublisher.textAtOrBefore(lines, 2_000L))
    }

    @Test
    fun alignedTextUsesPrimaryLineAnchorInsteadOfPlaybackPosition() {
        val translations = listOf(
            LyricLine(1_100L, 2_000L, "translation one"),
            LyricLine(3_900L, 5_000L, "translation two")
        )

        assertEquals(
            "translation one",
            FloatingLyricsPublisher.alignedTextAt(translations, primaryStartMs = 1_000L)
        )
    }

    @Test
    fun alignedTextRejectsUnrelatedCompanionLine() {
        assertEquals(
            "",
            FloatingLyricsPublisher.alignedTextAt(lines, primaryStartMs = 8_000L)
        )
    }
}
