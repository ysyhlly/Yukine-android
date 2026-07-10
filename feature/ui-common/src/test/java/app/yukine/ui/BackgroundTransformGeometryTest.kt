package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTransformGeometryTest {
    @Test
    fun previewShowsEntireLandscapeSourceAndCentersThePhoneCropFrame() {
        val frame = BackgroundTransformGeometry.previewCropFrame(
            sourceWidthPx = 1600f,
            sourceHeightPx = 900f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2400f
        )

        assertEquals(1080f, frame.imageWidthPx, 0.001f)
        assertEquals(607.5f, frame.imageHeightPx, 0.001f)
        assertEquals(273.375f, frame.frameWidthPx, 0.001f)
        assertEquals(607.5f, frame.frameHeightPx, 0.001f)
        assertEquals(403.3125f, frame.maxTranslationX(1f), 0.001f)
        assertEquals(0f, frame.maxTranslationY(1f), 0.001f)
    }

    @Test
    fun previewAndPageApplyTheSameNormalizedCropSelection() {
        val preview = BackgroundTransformGeometry.previewCropFrame(1600f, 900f, 1080f, 2400f)
        val page = BackgroundTransformGeometry.pageCropFrame(1600f, 900f, 1080f, 2400f)
        val scale = 1.75f
        val horizontalOffset = 0.6f

        val displayRatio = page.imageWidthPx / preview.imageWidthPx

        assertEquals(
            preview.translationX(scale, horizontalOffset) * displayRatio,
            page.translationX(scale, horizontalOffset),
            0.001f
        )
        assertEquals(
            horizontalOffset,
            preview.offsetFractionX(preview.translationX(scale, horizontalOffset), scale),
            0.001f
        )
        assertEquals(
            horizontalOffset,
            page.offsetFractionX(page.translationX(scale, horizontalOffset), scale),
            0.001f
        )
    }

    @Test
    fun pageCropFrameCoversTheViewportAtMinimumScale() {
        val frame = BackgroundTransformGeometry.pageCropFrame(
            sourceWidthPx = 1600f,
            sourceHeightPx = 900f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2400f
        )

        assertTrue(frame.imageWidthPx >= frame.frameWidthPx)
        assertTrue(frame.imageHeightPx >= frame.frameHeightPx)
        assertEquals(1080f, frame.frameWidthPx, 0.001f)
        assertEquals(2400f, frame.frameHeightPx, 0.001f)
    }

    @Test
    fun normalizedPanCanBeReprojectedAfterThePreviewViewportChanges() {
        val originalFrame = BackgroundTransformGeometry.previewCropFrame(1600f, 900f, 1080f, 2400f)
        val resizedFrame = BackgroundTransformGeometry.previewCropFrame(1600f, 900f, 1200f, 2400f)
        val scale = 1.5f
        val selectedX = 0.7f

        val savedFraction = originalFrame.offsetFractionX(
            originalFrame.translationX(scale, selectedX),
            scale
        )
        val reprojectedTranslation = resizedFrame.translationX(scale, savedFraction)

        assertEquals(selectedX, savedFraction, 0.001f)
        assertEquals(selectedX, resizedFrame.offsetFractionX(reprojectedTranslation, scale), 0.001f)
        assertTrue(kotlin.math.abs(reprojectedTranslation) <= resizedFrame.maxTranslationX(scale))
    }
}
