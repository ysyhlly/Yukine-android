package app.yukine.ui

import app.yukine.BackgroundTransform
import kotlin.math.max
import kotlin.math.min

/**
 * Shared geometry for page-background transforms.
 *
 * Legacy transforms were rendered by scaling a `ContentScale.Crop` image that already filled the
 * viewport. New crop-editor transforms instead describe a visible frame over the complete source
 * image. The editor shows that source with `Fit`; the page renders the same selected frame with
 * `Cover`. Both paths use [CropFrame], so a normalized pan selects the same part of the source in
 * the preview and on the real page.
 *
 * Persisted transforms clamp scale to at least `1`, so the final background always covers its page.
 */
object BackgroundTransformGeometry {
    /** Legacy `ContentScale.Crop` overflow used by pre crop-editor saved transforms. */
    fun maxTranslation(viewportSizePx: Float, scale: Float): Float {
        val overflow = viewportSizePx * (scale - 1f) / 2f
        return max(0f, overflow)
    }

    fun translationPx(viewportSizePx: Float, scale: Float, offsetFraction: Float): Float {
        return maxTranslation(viewportSizePx, scale) * offsetFraction.coerceIn(-1f, 1f)
    }

    /**
     * An image and a centered crop frame, both expressed in pixels before user zoom is applied.
     *
     * The image is intentionally represented separately from the frame: in the editor it is fitted
     * so the full source is visible, while on a page it is covered so the selected frame fills the
     * viewport. The normalized pan values remain interchangeable between those two renderings.
     */
    data class CropFrame(
        val imageWidthPx: Float,
        val imageHeightPx: Float,
        val frameWidthPx: Float,
        val frameHeightPx: Float
    ) {
        val isUsable: Boolean
            get() = imageWidthPx.isFinite() && imageHeightPx.isFinite() &&
                frameWidthPx.isFinite() && frameHeightPx.isFinite() &&
                imageWidthPx > 0f && imageHeightPx > 0f &&
                frameWidthPx > 0f && frameHeightPx > 0f

        fun maxTranslationX(scale: Float): Float = maxTranslation(imageWidthPx, frameWidthPx, scale)

        fun maxTranslationY(scale: Float): Float = maxTranslation(imageHeightPx, frameHeightPx, scale)

        fun translationX(scale: Float, offsetFraction: Float): Float =
            maxTranslationX(scale) * BackgroundTransformGeometry.normalizedOffset(offsetFraction)

        fun translationY(scale: Float, offsetFraction: Float): Float =
            maxTranslationY(scale) * BackgroundTransformGeometry.normalizedOffset(offsetFraction)

        fun offsetFractionX(translationPx: Float, scale: Float): Float =
            offsetFraction(translationPx, maxTranslationX(scale))

        fun offsetFractionY(translationPx: Float, scale: Float): Float =
            offsetFraction(translationPx, maxTranslationY(scale))

        private fun maxTranslation(imageSizePx: Float, frameSizePx: Float, scale: Float): Float {
            if (!isUsable) return 0f
            val safeScale = scale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
            return max(0f, (imageSizePx * safeScale - frameSizePx) / 2f)
        }

        private fun offsetFraction(translationPx: Float, maxTranslationPx: Float): Float {
            if (!translationPx.isFinite() || maxTranslationPx <= 0f) return 0f
            return (translationPx / maxTranslationPx).coerceIn(-1f, 1f)
        }

        companion object {
            val EMPTY = CropFrame(0f, 0f, 0f, 0f)
        }
    }

    /**
     * Fitted source and visible phone-aspect frame for the background picker.
     *
     * At scale 1, the entire source image remains visible and the frame shows exactly which area the
     * page will use. A wide image therefore exposes horizontal choices instead of being cropped
     * before the user can move it.
     */
    fun previewCropFrame(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): CropFrame = cropFrame(
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        imageScale = fitScale(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)
    )

    /**
     * Covered source and full-viewport frame for the rendered page background.
     */
    fun pageCropFrame(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): CropFrame = cropFrame(
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        imageScale = coverScale(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)
    )

    private fun cropFrame(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        imageScale: Float
    ): CropFrame {
        if (!hasValidDimensions(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx) ||
            !imageScale.isFinite() || imageScale <= 0f
        ) {
            return CropFrame.EMPTY
        }
        val cover = coverScale(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)
        if (!cover.isFinite() || cover <= 0f) return CropFrame.EMPTY
        val frameScale = imageScale / cover
        return CropFrame(
            imageWidthPx = sourceWidthPx * imageScale,
            imageHeightPx = sourceHeightPx * imageScale,
            frameWidthPx = viewportWidthPx * frameScale,
            frameHeightPx = viewportHeightPx * frameScale
        )
    }

    private fun fitScale(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Float {
        if (!hasValidDimensions(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)) return 0f
        return min(viewportWidthPx / sourceWidthPx, viewportHeightPx / sourceHeightPx)
    }

    private fun coverScale(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Float {
        if (!hasValidDimensions(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)) return 0f
        return max(viewportWidthPx / sourceWidthPx, viewportHeightPx / sourceHeightPx)
    }

    private fun hasValidDimensions(vararg dimensions: Float): Boolean =
        dimensions.all { it.isFinite() && it > 0f }

    private fun normalizedOffset(value: Float): Float =
        value.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
}
