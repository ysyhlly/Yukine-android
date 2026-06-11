package app.echo.next.ui

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
 * "Paper" soft-block surface. Replaces the former translucent glass (gradient fill + heavy border)
 * with an opaque, calm card: a single solid surface fill, a gentle drop shadow on light themes for
 * a soft lift, and a hairline border on dark themes for separation (where shadows don't read).
 * This keeps the same signature so every former glass call site updates at once.
 */
@Composable
fun Modifier.echoGlassLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    val dark = palette.background.luminance() < 0.5f
    val elevation = if (dark) 0.dp else 6.dp
    val shadowColor = palette.shadow.copy(alpha = if (dark) 0f else 0.16f)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor
        )
        .clip(shape)
        .background(palette.surface)
        .then(
            if (dark) {
                Modifier.border(1.dp, palette.border.copy(alpha = 0.5f), shape)
            } else {
                Modifier
            }
        )
}
