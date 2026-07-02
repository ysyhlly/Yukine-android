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
import androidx.compose.ui.unit.dp

@Composable
fun EchoGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = EchoShapes.medium,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val p = EchoTheme.colors()
    Box(
        modifier = modifier
            .echoGlassLayer(p, shape)
            .padding(contentPadding),
        content = content
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
 * Paper-like card surface. Light themes get a minimal drop shadow + subtle border for definition.
 * Dark themes get a hairline border only. The result is a clean, print-quality card feel.
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
            .shadow(
                elevation = 1.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.shadow.copy(alpha = 0.05f),
                spotColor = palette.shadow.copy(alpha = 0.05f)
            )
            .clip(shape)
            .background(palette.surface)
    }
}
