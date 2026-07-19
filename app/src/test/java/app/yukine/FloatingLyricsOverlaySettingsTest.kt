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
    fun normalizesValuesToSupportedRangesAndRepairsNonFinitePositions() {
        val normalized = FloatingLyricsOverlaySettings(
            textSizeSp = 100,
            widthPercent = 1,
            positionXFraction = Float.NaN,
            positionYFraction = Float.POSITIVE_INFINITY,
            backgroundOpacityPercent = -1
        ).normalized()

        assertEquals(FloatingLyricsOverlaySettings.MAX_TEXT_SIZE_SP, normalized.textSizeSp)
        assertEquals(FloatingLyricsOverlaySettings.MIN_WIDTH_PERCENT, normalized.widthPercent)
        assertEquals(
            FloatingLyricsOverlaySettings.DEFAULT_POSITION_X_FRACTION,
            normalized.positionXFraction
        )
        assertEquals(
            FloatingLyricsOverlaySettings.DEFAULT_POSITION_Y_FRACTION,
            normalized.positionYFraction
        )
        assertEquals(
            FloatingLyricsOverlaySettings.MIN_BACKGROUND_OPACITY_PERCENT,
            normalized.backgroundOpacityPercent
        )
    }

    @Test
    fun persistsEveryOverlayParameter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clearPreferences(context)
        val store = FloatingLyricsOverlaySettingsStore(context)
        val expected = FloatingLyricsOverlaySettings(
            textSizeSp = 22,
            widthPercent = 72,
            positionXFraction = 0.26f,
            positionYFraction = 0.36f,
            backgroundOpacityPercent = 48,
            transparentBackground = true
        )

        store.save(expected)

        val restored = store.load()
        assertEquals(expected, restored)
        assertTrue(restored.transparentBackground)
        assertFalse(restored.backgroundOpacityPercent == 0)
    }

    @Test
    fun migratesLegacyVerticalPositionAndClearsPersistentClickThrough() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = clearPreferences(context)
        preferences.edit()
            .putInt("vertical_position_percent", 36)
            .putBoolean("click_through", true)
            .commit()

        val restored = FloatingLyricsOverlaySettingsStore(context).load()

        assertEquals(0.36f, restored.positionYFraction)
        assertEquals(
            FloatingLyricsOverlaySettings.DEFAULT_POSITION_X_FRACTION,
            restored.positionXFraction
        )
        assertFalse(preferences.contains("vertical_position_percent"))
        assertFalse(preferences.contains("click_through"))
    }

    @Test
    fun clickThroughAddsNotTouchableWithoutLosingBaseFlags() {
        val flags = FloatingLyricsOverlayWindowPolicy.windowFlags(
            FloatingLyricsInteraction.ClickThrough
        )

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun interactiveWindowRemainsTouchable() {
        val flags = FloatingLyricsOverlayWindowPolicy.windowFlags(
            FloatingLyricsInteraction.Interactive
        )

        assertFalse(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }

    @Test
    fun clickThroughAlphaStaysBelowSystemObscuringLimit() {
        val alpha = FloatingLyricsOverlayWindowPolicy.windowAlpha(
            FloatingLyricsInteraction.ClickThrough,
            maximumObscuringOpacity = 0.8f
        )

        assertTrue(alpha < 0.8f)
        assertEquals(
            1f,
            FloatingLyricsOverlayWindowPolicy.windowAlpha(
                FloatingLyricsInteraction.Interactive,
                maximumObscuringOpacity = 0.8f
            )
        )
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

    private fun clearPreferences(context: Context) =
        context.getSharedPreferences("floating_lyrics_overlay", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
}
