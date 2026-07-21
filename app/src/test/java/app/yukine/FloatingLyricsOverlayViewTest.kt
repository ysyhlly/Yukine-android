package app.yukine

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
class FloatingLyricsOverlayViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun compactSurfaceDistinguishesTapFromDrag() {
        val actions = mutableListOf<FloatingLyricsOverlayAction>()
        val overlay = FloatingLyricsOverlayView(
            context,
            FloatingLyricsOverlaySettings(),
            actions::add
        )
        val compactSurface = overlay.root.getChildAt(0)

        compactSurface.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        compactSurface.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 20f, 20f))

        assertEquals(listOf(FloatingLyricsOverlayAction.ToggleExpanded), actions)
        actions.clear()

        compactSurface.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        compactSurface.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 90f, 100f))
        compactSurface.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 90f, 100f))

        assertTrue(actions.any { it is FloatingLyricsOverlayAction.DragBy })
        assertEquals(FloatingLyricsOverlayAction.DragFinished, actions.last())
        assertFalse(actions.contains(FloatingLyricsOverlayAction.ToggleExpanded))
    }

    @Test
    fun expandedPlaybackButtonDoesNotEmitDragActions() {
        val actions = mutableListOf<FloatingLyricsOverlayAction>()
        val overlay = FloatingLyricsOverlayView(
            context,
            FloatingLyricsOverlaySettings(),
            actions::add
        )
        overlay.renderPresentation(
            FloatingLyricsPresentation.Visible(
                FloatingLyricsMode.Expanded,
                FloatingLyricsInteraction.Interactive
            ),
            animate = false
        )
        val playButton = descendants(overlay.root)
            .filterIsInstance<ImageButton>()
            .first { it.contentDescription == context.getString(R.string.play) }

        playButton.performClick()

        assertEquals(listOf(FloatingLyricsOverlayAction.PlayPause), actions)
        assertFalse(actions.any { it is FloatingLyricsOverlayAction.DragBy })
    }

    @Test
    fun lyricTextSupportsTwoLinesAndMinimumTouchHeight() {
        val overlay = FloatingLyricsOverlayView(
            context,
            FloatingLyricsOverlaySettings(textSizeSp = 30)
        ) { }
        val compactSurface = overlay.root.getChildAt(0)

        assertEquals(2, overlay.lyricsView.maxLines)
        assertTrue(compactSurface.minimumHeight >= 48)
        assertEquals(30f, overlay.lyricsView.textSize / context.resources.displayMetrics.scaledDensity)
    }

    @Test
    fun bindStateShowsTranslationAndRomanizationWhenPresent() {
        val overlay = FloatingLyricsOverlayView(
            context,
            FloatingLyricsOverlaySettings(),
        ) { }
        overlay.bindState(
            FloatingLyricsState(
                activeLine = "\u539f\u6587\u6b4c\u8bcd",
                activeTranslation = "Translation text",
                activeRomanization = "genbun kashi",
                visible = true
            ),
            artwork = null
        )

        assertEquals(View.VISIBLE, overlay.translationView.visibility)
        assertEquals("Translation text", overlay.translationView.text.toString())
        assertEquals(View.VISIBLE, overlay.romanizationView.visibility)
        assertEquals("genbun kashi", overlay.romanizationView.text.toString())
    }

    @Test
    fun translationAndRomanizationHiddenWhenEmpty() {
        val overlay = FloatingLyricsOverlayView(
            context,
            FloatingLyricsOverlaySettings(),
        ) { }
        overlay.bindState(
            FloatingLyricsState(
                activeLine = "\u539f\u6587\u6b4c\u8bcd",
                activeTranslation = "",
                activeRomanization = "",
                visible = true
            ),
            artwork = null
        )

        assertEquals(View.GONE, overlay.translationView.visibility)
        assertEquals(View.GONE, overlay.romanizationView.visibility)
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                yieldAll(descendants(root.getChildAt(index)))
            }
        }
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 16L, action, x, y, 0)
}
