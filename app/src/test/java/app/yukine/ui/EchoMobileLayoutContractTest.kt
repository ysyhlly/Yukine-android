package app.yukine.ui

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoMobileLayoutContractTest {
    @Test
    fun glassDefaultsUseRenderEffectOnAndroid12AndTransparentFallbackBelow() {
        assertEquals(Build.VERSION_CODES.S, EchoGlassDefaults.RENDER_EFFECT_MIN_API)
        assertEquals(18f, EchoGlassDefaults.BLUR_RADIUS_DP)
        assertEquals(1.08f, EchoGlassDefaults.SATURATION)
        assertEquals(0.72f, EchoGlassDefaults.ALPHA)

        val spec = EchoGlassSpec()
        assertEquals(EchoGlassDefaults.BLUR_RADIUS_DP, spec.blurRadiusDp)
        assertEquals(EchoGlassDefaults.SATURATION, spec.saturation)
        assertEquals(EchoGlassDefaults.ALPHA, spec.alpha)
    }

    @Test
    fun mobileShellMetricsMatchDesktopInspiredP0LayoutContract() {
        assertEquals(132f, EchoMobileLayoutMetrics.nowBarHeight.value)
        assertEquals(EchoMobileLayoutMetrics.nowBarHeight, EchoMobileLayoutMetrics.nowBarExpandedHeight)
        assertEquals(48f, EchoMobileLayoutMetrics.nowBarArtworkSize.value)
        assertEquals(6f, EchoMobileLayoutMetrics.nowBarArtworkCornerRadius.value)
        assertEquals(18f, EchoMobileLayoutMetrics.nowBarProgressHeight.value)
        assertEquals(22f, EchoMobileLayoutMetrics.bottomTabIconSize.value)
        assertEquals(8f, EchoMobileLayoutMetrics.bottomTabVerticalPadding.value)
        assertEquals(220f, EchoMobileLayoutMetrics.nowPlayingArtworkSize.value)
        assertEquals(12f, EchoMobileLayoutMetrics.nowPlayingArtworkCornerRadius.value)
        assertEquals(300f, EchoMobileLayoutMetrics.lyricsPanelMinHeight.value)
        assertEquals(380f, EchoMobileLayoutMetrics.lyricsPanelMaxHeight.value)
        assertEquals(292f, EchoMobileLayoutMetrics.lyricsListHeight.value)
        assertTrue(EchoMobileLayoutMetrics.lyricsPanelMaxHeight > EchoMobileLayoutMetrics.lyricsPanelMinHeight)
    }
}
