package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingQualityPreferenceTest {
    @Test
    fun normalizeAcceptsKnownValuesAndDefaultsUnknownToHigh() {
        assertEquals(StreamingQualityPreference.AUTO, StreamingQualityPreference.normalize(" AUTO "))
        assertEquals(StreamingQualityPreference.LOSSLESS, StreamingQualityPreference.normalize("lossless"))
        assertEquals(StreamingQualityPreference.HIGH, StreamingQualityPreference.normalize("unknown"))
        assertEquals(StreamingQualityPreference.HIGH, StreamingQualityPreference.normalize(null))
    }

    @Test
    fun optionsExposeStablePreferenceOrder() {
        assertEquals(
            listOf(
                StreamingQualityPreference.AUTO,
                StreamingQualityPreference.STANDARD,
                StreamingQualityPreference.HIGH,
                StreamingQualityPreference.LOSSLESS,
                StreamingQualityPreference.HIRES
            ),
            StreamingQualityPreference.options()
        )
    }

    @Test
    fun ceilingAndValueForMapToAudioQuality() {
        assertEquals(StreamingAudioQuality.STANDARD, StreamingQualityPreference.ceilingFor("standard"))
        assertEquals(StreamingAudioQuality.HIGH, StreamingQualityPreference.ceilingFor("high"))
        assertEquals(StreamingAudioQuality.LOSSLESS, StreamingQualityPreference.ceilingFor("auto"))
        assertEquals(StreamingAudioQuality.HIRES, StreamingQualityPreference.ceilingFor("hires"))

        assertEquals(StreamingQualityPreference.STANDARD, StreamingQualityPreference.valueFor(StreamingAudioQuality.STANDARD))
        assertEquals(StreamingQualityPreference.HIGH, StreamingQualityPreference.valueFor(StreamingAudioQuality.HIGH))
        assertEquals(StreamingQualityPreference.LOSSLESS, StreamingQualityPreference.valueFor(StreamingAudioQuality.LOSSLESS))
        assertEquals(StreamingQualityPreference.HIRES, StreamingQualityPreference.valueFor(StreamingAudioQuality.HIRES))
        assertTrue(StreamingQualityPreference.options().contains(StreamingQualityPreference.defaultValue()))
    }
}
