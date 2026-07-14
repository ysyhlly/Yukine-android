package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformSamplesTest {
    @Test
    fun samplesAreStableAndDefensivelyCopied() {
        val source = floatArrayOf(0.1f, 0.4f, 0.8f)
        val samples = WaveformSamples.of(source)

        source[1] = 1.0f

        assertEquals(WaveformSamples.of(floatArrayOf(0.1f, 0.4f, 0.8f)), samples)
        assertEquals(WaveformSamples.of(floatArrayOf(0.1f, 0.4f, 0.8f)).hashCode(), samples.hashCode())
        assertEquals(3, samples.size)
    }
}
