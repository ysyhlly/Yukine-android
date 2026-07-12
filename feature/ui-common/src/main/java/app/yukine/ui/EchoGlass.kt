package app.yukine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

class EchoCompositeBackdrop(
    val sourceLayer: GraphicsLayer,
    val sourceOrigin: () -> Offset
)

val LocalEchoCompositeBackdrop = androidx.compose.runtime.staticCompositionLocalOf<EchoCompositeBackdrop?> { null }

/**
 * Makes a floating surface participate in pointer hit testing across its complete bounds.
 *
 * Child controls keep priority because this observer does not consume their events. Its presence on
 * the topmost surface prevents otherwise empty or transparent areas from targeting content behind it.
 */
fun Modifier.blockPointerInputBehind(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
    }
}

@Composable
fun EchoGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = EchoShapes.medium,
    elevation: Dp = EchoElevations.card,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val p = EchoTheme.colors()
    Box(
        modifier = modifier
            .echoFloatingLayer(p, shape, elevation)
            .echoGlassLayer(p, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun Modifier.echoFloatingLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium,
    elevation: Dp = EchoElevations.card
): Modifier {
    if (elevation <= 0.dp) return this
    val dark = palette.background.luminance() < 0.5f
    val ambientAlpha = if (dark) 0.24f else 0.12f
    val spotAlpha = if (dark) 0.34f else 0.18f
    return shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = palette.shadow.copy(alpha = ambientAlpha),
        spotColor = palette.shadow.copy(alpha = spotAlpha)
    )
}

/**
 * Frosted-aware fill color for `Surface(color = ...)` call sites that don't go through
 * [echoGlassLayer]. Returns a semi-transparent version of [base] whenever Gaussian blur is
 * enabled, including when Yukine's built-in gradient is used instead of a custom wallpaper.
 */
@Composable
fun echoCardColor(base: Color = EchoTheme.colors().surface): Color {
    if (!LocalEchoGlassEnabled.current) {
        return base
    }
    return base.copy(alpha = EchoGlassDefaults.normalizeSurfaceOpacity(LocalEchoGlassOpacity.current))
}

@Composable
fun Modifier.echoGaussianBackdrop(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    if (!LocalEchoGlassEnabled.current) return this
    val compositeBackdrop = LocalEchoCompositeBackdrop.current
    if (compositeBackdrop != null) {
        val blurredOutputLayer = rememberGraphicsLayer()
        val radiusPx = with(LocalDensity.current) {
            EchoGlassDefaults.normalizeBlurRadius(LocalEchoGlassBlurRadius.current).dp.toPx()
        }
        var targetOrigin by remember { mutableStateOf(Offset.Zero) }
        return this
            .clip(shape)
            .onGloballyPositioned { targetOrigin = it.positionInRoot() }
            .drawWithContent {
                blurredOutputLayer.renderEffect = BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp
                )
                val outputSize = IntSize(
                    width = size.width.roundToInt().coerceAtLeast(1),
                    height = size.height.roundToInt().coerceAtLeast(1)
                )
                val sourceOrigin = compositeBackdrop.sourceOrigin()
                blurredOutputLayer.record(size = outputSize) {
                    withTransform({
                        translate(
                            left = sourceOrigin.x - targetOrigin.x,
                            top = sourceOrigin.y - targetOrigin.y
                        )
                    }) {
                        drawLayer(compositeBackdrop.sourceLayer)
                    }
                }
                drawLayer(blurredOutputLayer)
                drawContent()
            }
    }
    val hazeState = LocalEchoHazeState.current ?: return this
    val opacity = EchoGlassDefaults.normalizeSurfaceOpacity(LocalEchoGlassOpacity.current)
    return hazeChild(
        state = hazeState,
        shape = shape,
        style = HazeStyle(
            // Keep the Haze base transparent so opacity remains visible over both a custom
            // wallpaper and Yukine's built-in gradient background.
            backgroundColor = Color.Transparent,
            tint = HazeTint(Color.Transparent),
            blurRadius = EchoGlassDefaults.normalizeBlurRadius(LocalEchoGlassBlurRadius.current).dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(palette.surface.copy(alpha = opacity))
        )
    )
}

/**
 * Paper-like card material. Elevation is owned by [EchoGlassSurface] or [echoFloatingLayer], while
 * this modifier owns clipping, fill, and border treatment.
 *
 * When Gaussian blur is enabled, cards switch to a frosted, semi-transparent fill over either
 * the built-in gradient or a custom wallpaper, with a brighter hairline border for legibility.
 */
@Composable
fun Modifier.echoGlassLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    val dark = palette.background.luminance() < 0.5f
    val frosted = LocalEchoGlassEnabled.current
    val opacity = EchoGlassDefaults.normalizeSurfaceOpacity(LocalEchoGlassOpacity.current)
    val effectModifier = echoGaussianBackdrop(palette, shape)
    if (frosted) {
        // 真正的 backdrop 高斯模糊上叠加可调表面色，并用高光描边定义边缘。
        val borderColor = if (dark) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.White.copy(alpha = 0.55f)
        }
        return effectModifier
            .clip(shape)
            .background(palette.surface.copy(alpha = opacity))
            .border(1.dp, borderColor, shape)
    }
    return if (dark) {
        this
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border.copy(alpha = 0.4f), shape)
    } else {
        this
            .clip(shape)
            .background(palette.surface)
    }
}
