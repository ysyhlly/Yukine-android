package app.yukine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun EchoArtworkFallback(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    textSize: TextUnit = 22.sp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(fallbackColor(title, subtitle)),
        contentAlignment = Alignment.Center
    ) {
        FallbackInitials(title = title, subtitle = subtitle, textSize = textSize)
    }
}

@Composable
private fun BoxScope.FallbackInitials(title: String, subtitle: String, textSize: TextUnit) {
    Text(
        text = initialsFor(title, subtitle),
        color = Color.White,
        fontSize = textSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.align(Alignment.Center)
    )
}

private fun fallbackColor(title: String, subtitle: String): Color {
    val seed = seedFor(title, subtitle)
    val colors = listOf(
        Color(0xFF246BFE),
        Color(0xFF087D70),
        Color(0xFFD2386C),
        Color(0xFF6D4AFF),
        Color(0xFF138A4F),
        Color(0xFF047C9C),
        Color(0xFF5F7500),
        Color(0xFF596273)
    )
    return colors[(seed.hashCode() and Int.MAX_VALUE) % colors.size]
}

private fun initialsFor(title: String, subtitle: String): String {
    val seed = seedFor(title, subtitle)
    val clean = seed.filter { it.isLetterOrDigit() }.take(2)
    return if (clean.isEmpty()) {
        "E"
    } else {
        clean.uppercase(Locale.ROOT)
    }
}

private fun seedFor(title: String, subtitle: String): String {
    val cleanTitle = title.trim()
    if (cleanTitle.isNotEmpty() && cleanTitle != "未选择歌曲") {
        return cleanTitle
    }
    val cleanSubtitle = subtitle.trim()
    return if (cleanSubtitle.isEmpty()) "Yukine" else cleanSubtitle
}
