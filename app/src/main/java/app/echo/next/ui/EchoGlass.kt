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
 * Paper-like card surface. Light themes get a minimal drop shadow + subtle border for definition.
 * Dark themes get a hairline border only. The result is a clean, print-quality card feel.
 */
@Composable
fun Modifier.echoGlassLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    val dark = palette.background.luminance() < 0.5f
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
