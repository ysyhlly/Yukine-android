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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
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

@Composable
fun Modifier.echoGlassLayer(
    palette: EchoPalette = EchoTheme.colors(),
    shape: Shape = EchoShapes.medium
): Modifier {
    val glassFill = Brush.verticalGradient(
        colors = listOf(
            palette.surface.copy(alpha = palette.glass.alpha.coerceIn(0.4f, 0.94f)),
            palette.surfaceVariant.copy(alpha = (palette.glass.alpha - 0.1f).coerceIn(0.32f, 0.86f))
        )
    )
    return this
        .clip(shape)
        .background(glassFill)
        .border(1.dp, palette.border.copy(alpha = 0.42f), shape)
}
