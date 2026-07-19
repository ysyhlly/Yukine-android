package app.yukine

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FloatingLyricsOverlaySettingsTest {
    @Test
    fun normalizesValuesToSupportedRanges() {
        val normalized = FloatingLyricsOverlaySettings(
            textSizeSp = 100,
            widthPercent = 1,
            verticalPositionPercent = 100,
            backgroundOpacityPercent = -1
        ).normalized()

        assertEquals(FloatingLyricsOverlaySettings.MAX_TEXT_SIZE_SP, normalized.textSizeSp)
        assertEquals(FloatingLyricsOverlaySettings.MIN_WIDTH_PERCENT, normalized.widthPercent)
        assertEquals(
            FloatingLyricsOverlaySettings.MAX_VERTICAL_POSITION_PERCENT,
            normalized.verticalPositionPercent
        )
        assertEquals(
            FloatingLyricsOverlaySettings.MIN_BACKGROUND_OPACITY_PERCENT,
            normalized.backgroundOpacityPercent
        )
    }

    @Test
    fun persistsEveryOverlayParameter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("floating_lyrics_overlay", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = FloatingLyricsOverlaySettingsStore(context)
        val expected = FloatingLyricsOverlaySettings(
            textSizeSp = 22,
            widthPercent = 72,
            verticalPositionPercent = 36,
            backgroundOpacityPercent = 48,
            transparentBackground = true,
            clickThrough = true
        )

        store.save(expected)

        val restored = store.load()
        assertEquals(expected, restored)
        assertTrue(restored.transparentBackground)
        assertTrue(restored.clickThrough)
        assertFalse(restored.backgroundOpacityPercent == 0)
    }

    @Test
    fun clickThroughAddsNotTouchableWithoutLosingBaseFlags() {
        val flags = FloatingLyricsOverlayWindowPolicy.windowFlags(clickThrough = true)

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun transparentBackgroundAlwaysUsesZeroAlpha() {
        val transparent = FloatingLyricsOverlaySettings(
            backgroundOpacityPercent = 100,
            transparentBackground = true
        )
        val translucent = FloatingLyricsOverlaySettings(
            backgroundOpacityPercent = 50,
            transparentBackground = false
        )

        assertEquals(0, FloatingLyricsOverlayWindowPolicy.backgroundAlpha(transparent))
        assertEquals(127, FloatingLyricsOverlayWindowPolicy.backgroundAlpha(translucent))
    }
}
