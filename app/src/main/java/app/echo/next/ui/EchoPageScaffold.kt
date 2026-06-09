package app.echo.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object EchoPageDefaults {
    val horizontalPadding: Dp = 16.dp
    val topPadding: Dp = 12.dp
    val bottomPadding: Dp = 100.dp
    val itemSpacing: Dp = 12.dp
    val sectionSpacing: Dp = 16.dp
}

@Composable
fun Modifier.echoPageBackground(): Modifier {
    val p = EchoTheme.colors()
    return this
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                listOf(
                    p.backgroundAlt,
                    p.background,
                    p.surfaceVariant.copy(alpha = 0.34f)
                )
            )
        )
}

fun echoPagePadding(
    top: Dp = EchoPageDefaults.topPadding,
    bottom: Dp = EchoPageDefaults.bottomPadding
): PaddingValues = PaddingValues(
    start = EchoPageDefaults.horizontalPadding,
    top = top,
    end = EchoPageDefaults.horizontalPadding,
    bottom = bottom
)

@Composable
fun EchoPageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val p = EchoTheme.colors()
    if (title.isBlank() && subtitle.isNullOrBlank()) return
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        androidx.compose.foundation.layout.Column {
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = EchoTypography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = p.heading,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EchoSectionTitle(title: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    if (title.isBlank()) return
    Text(
        title,
        style = EchoTypography.title,
        color = p.text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 4.dp, end = 2.dp, bottom = 2.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun EchoEmptyCard(message: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 26.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = EchoTypography.body.copy(fontWeight = FontWeight.Medium),
                color = p.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
