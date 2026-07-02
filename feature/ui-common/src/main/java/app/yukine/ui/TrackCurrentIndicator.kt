package app.yukine.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TrackCurrentIndicator(active: Boolean, height: Dp = 44.dp) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .width(3.dp)
            .height(height),
        shape = EchoShapes.small,
        color = if (active) p.accent else Color.Transparent
    ) {}
}
