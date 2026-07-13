package app.yukine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomBackgroundAccentExtractorTest {
    @Test
    fun accentSourcePrefersSharedThenSettingsBackground() {
        assertEquals(
            "content://shared",
            PageBackgrounds(
                sharedUri = "content://shared",
                settingsUri = "content://settings"
            ).accentSourceUri()
        )
        assertEquals(
            "content://settings",
            PageBackgrounds(
                settingsUri = "content://settings",
                homeUri = "content://home"
            ).accentSourceUri()
        )
    }

    @Test
    fun dominantAccentUsesTheVibrantBackgroundCluster() {
        val pixels = IntArray(100) { index ->
            when {
                index < 70 -> Color.rgb(24, 145, 205)
                index < 90 -> Color.rgb(245, 245, 245)
                else -> Color.rgb(30, 30, 30)
            }
        }

        val accent = CustomBackgroundAccentExtractor.dominantAccent(pixels)

        assertNotNull(accent)
        val hsv = FloatArray(3)
        Color.colorToHSV(accent!!, hsv)
        assertTrue(hsv[0] in 190f..215f)
        assertTrue(hsv[1] >= 0.45f)
    }
}
