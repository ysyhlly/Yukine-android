package app.yukine.ui

import app.yukine.BackgroundTransform
import kotlin.math.max

/**
 * Shared geometry for rendering a [BackgroundTransform] over a `ContentScale.Crop` background.
 *
 * The background image composable is sized to exactly cover the viewport (Crop), so scaling that
 * composable by `scale` produces a symmetric overflow of `size * (scale - 1) / 2` on each axis.
 * A normalized pan fraction in [-1, 1] maps linearly onto that overflow. When `scale <= 1` there is
 * no overflow, so the image stays centered (and may letterbox, revealing the page gradient).
 *
 * Keeping this in one place guarantees the live preview and the actual page render agree pixel for
 * pixel.
 */
object BackgroundTransformGeometry {
    fun maxTranslation(viewportSizePx: Float, scale: Float): Float {
        val overflow = viewportSizePx * (scale - 1f) / 2f
        return max(0f, overflow)
    }

    fun translationPx(viewportSizePx: Float, scale: Float, offsetFraction: Float): Float {
        return maxTranslation(viewportSizePx, scale) * offsetFraction.coerceIn(-1f, 1f)
    }
}
