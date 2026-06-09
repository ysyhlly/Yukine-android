package app.echo.next.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBarWaveformRangeTest {
    @Test
    fun visibleWaveformPeakRangeFallsBackForMissingData() {
        assertEquals(0f to 1f, visibleWaveformPeakRange(null, 12))
        assertEquals(0f to 1f, visibleWaveformPeakRange(floatArrayOf(0.2f, 0.4f), 0))
    }

    @Test
    fun visibleWaveformPeakRangeUsesPercentilesInsteadOfExtremePeak() {
        val waveform = floatArrayOf(
            0.01f, 0.12f, 0.14f, 0.16f, 0.18f, 0.20f,
            0.22f, 0.24f, 0.26f, 0.28f, 0.30f, 1.0f
        )

        val (floor, span) = visibleWaveformPeakRange(waveform, waveform.size)

        assertTrue("floor should stay near the audible low percentile", floor in 0.07f..0.11f)
        assertTrue("span should ignore the single outlier peak", span in 0.18f..0.25f)
    }

    @Test
    fun visibleWaveformPeakRangeOnlyUsesGeneratedBars() {
        val waveform = floatArrayOf(0.10f, 0.20f, 0.30f, 1.0f, 1.0f, 1.0f)

        val (_, span) = visibleWaveformPeakRange(waveform, 3)

        assertTrue("ungenerated tail peaks must not flatten the visible range", span < 0.22f)
    }

    @Test
    fun visibleWaveformBarsOnlyScansGeneratedPrefix() {
        assertTrue(hasVisibleWaveformBars(floatArrayOf(0.0f, 0.016f), 2))
        assertEquals(false, hasVisibleWaveformBars(floatArrayOf(0.0f, 0.014f), 2))
        assertEquals(false, hasVisibleWaveformBars(floatArrayOf(0.0f, 0.9f), 1))
        assertEquals(false, hasVisibleWaveformBars(floatArrayOf(0.9f), 0))
        assertTrue(hasVisibleWaveformBars(floatArrayOf(0.0f, 0.03f), 99))
    }

    @Test
    fun cachedProgressUsesServiceProgressBeforeWaveformBarsExist() {
        assertEquals(0.35f, waveformCachedProgressForDraw(0.35f, 0), 0.0001f)
        assertEquals(0.72f, waveformCachedProgressForDraw(0.72f, 12), 0.0001f)
        assertEquals(1f, waveformCachedProgressForDraw(0f, 0), 0.0001f)
        assertEquals(1f, waveformCachedProgressForDraw(2f, 0), 0.0001f)
    }
}
