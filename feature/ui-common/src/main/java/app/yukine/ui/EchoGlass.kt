package app.yukine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * [echoGlassLayer]. Returns a semi-transparent version of [base] when a custom background is
 * active so the wallpaper shows through, otherwise [base] unchanged.
 */
@Composable
fun echoCardColor(base: Color = EchoTheme.colors().surface): Color {
    if (!LocalEchoCustomBackground.current) {
        return base
    }
    val dark = EchoTheme.colors().background.luminance() < 0.5f
    return base.copy(alpha = if (dark) 0.46f else 0.62f)
}

/**
 * Paper-like card material. Elevation is owned by [EchoGlassSurface] or [echoFloatingLayer], while
 * this modifier owns clipping, fill, and border treatment.
 *
 * When a custom background image is active ([LocalEchoCustomBackground]), cards switch to a
 * frosted, semi-transparent fill so the wallpaper shows through, with a brighter hairline border
 * to keep edges legible against arbitrary imagery.
 */
@Composable
fun Modifier.echoGlassLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    val dark = palette.background.luminance() < 0.5f
    val frosted = LocalEchoCustomBackground.current
    if (frosted) {
        // 磨砂微透：卡片半透显出背景，配一道高光描边定义边缘。
        val fillAlpha = if (dark) 0.46f else 0.62f
        val borderColor = if (dark) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.White.copy(alpha = 0.55f)
        }
        return this
            .clip(shape)
            .background(palette.surface.copy(alpha = fillAlpha))
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
