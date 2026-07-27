package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure motion math and timing contracts used by route/page transitions and shared helpers.
 * Drives [EchoMotion] entry points directly (no re-implementation in the test).
 */
class EchoMotionTest {

    @Test
    fun horizontalSlideOffset_usesSharedDivisor() {
        val width = 1080
        val expected = width / EchoMotion.PAGE_SLIDE_DIVISOR
        assertEquals(expected, EchoMotion.horizontalSlideOffset(width))
        assertEquals(0, EchoMotion.horizontalSlideOffset(0))
        assertEquals(0, EchoMotion.horizontalSlideOffset(width, divisor = 0))
        assertEquals(0, EchoMotion.horizontalSlideOffset(-10))
    }

    @Test
    fun pageAndTrackTimings_stayCoherent() {
        assertTrue(EchoMotion.CROSSFADE_MS > EchoMotion.FAST_CROSSFADE_MS)
        assertTrue(EchoMotion.PAGE_SLIDE_DIVISOR >= 16)
        assertTrue(EchoMotion.CONFIRM_PULSE_SCALE > 1f)
        assertTrue(EchoMotion.BREATH_MS >= 400)
        // Smoke: transition factories construct without throwing.
        EchoMotion.pageContentTransition()
        EchoMotion.trackContentTransition()
    }
}
