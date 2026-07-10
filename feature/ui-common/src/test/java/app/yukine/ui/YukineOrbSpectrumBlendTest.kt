package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YukineOrbSpectrumBlendTest {
    @Test
    fun liveBandRemainsClearlyVisibleOverStaticArtwork() {
        val quietLiveBand = blendYukineOrbSpectrumBand(base = 0.9f, realtime = 0.05f)
        val loudLiveBand = blendYukineOrbSpectrumBand(base = 0.9f, realtime = 0.75f)

        assertTrue("realtime intensity must visibly change the ring", loudLiveBand - quietLiveBand > 0.55f)
        assertTrue("a loud live band should dominate the static baseline", loudLiveBand > 0.8f)
    }

    @Test
    fun blendClampsInvalidSpectrumValues() {
        assertEquals(0f, blendYukineOrbSpectrumBand(base = -1f, realtime = -1f), 0f)
        assertEquals(1f, blendYukineOrbSpectrumBand(base = 2f, realtime = 2f), 0f)
    }
}
