package app.yukine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class EchoIconKind {
    Mark,
    Search,
    Library,
    Collections,
    PlaylistAdd,
    Queue,
    Play,
    Pause,
    Previous,
    Next,
    Heart,
    Shuffle,
    Repeat,
    Network,
    Settings,
    Palette,
    Swatch,
    Gauge,
    Volume,
    Timer,
    Lyrics,
    Back,
    Artist,
    Edit,
    Delete,
    Remove,
    Import,
    Folder,
    Check,
    ArrowUp,
    ArrowDown,
    Sync,
    Action,
    Refresh,
    Language,
    ChevronRight,
    More,
    Sparkle
}

@Composable
fun EchoIcon(
    kind: EchoIconKind,
    modifier: Modifier = Modifier.size(22.dp),
    color: Color = EchoTheme.colors().text
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (kind) {
            EchoIconKind.Mark -> {
                val barWidth = w * 0.16f
                val bottom = h * 0.78f
                drawRoundRect(color, Offset(w * 0.18f, h * 0.38f), Size(barWidth, bottom - h * 0.38f), CornerRadius(barWidth, barWidth))
                drawRoundRect(color, Offset(w * 0.42f, h * 0.18f), Size(barWidth, bottom - h * 0.18f), CornerRadius(barWidth, barWidth))
                drawRoundRect(color, Offset(w * 0.66f, h * 0.44f), Size(barWidth, bottom - h * 0.44f), CornerRadius(barWidth, barWidth))
                drawRoundRect(color, Offset(w * 0.12f, h * 0.86f), Size(w * 0.76f, h * 0.08f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            EchoIconKind.Library -> {
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.36f, h * 0.68f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.5f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.76f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, h * 0.3f), Offset(w * 0.76f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Search -> {
                drawCircle(color, radius = w * 0.25f, center = Offset(w * 0.43f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.61f, h * 0.61f), Offset(w * 0.84f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Collections -> {
                drawRoundRect(color, Offset(w * 0.22f, h * 0.2f), Size(w * 0.5f, h * 0.5f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.36f, h * 0.8f), Offset(w * 0.78f, h * 0.8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.8f), Offset(w * 0.78f, h * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.47f, h * 0.45f), style = stroke)
            }
            EchoIconKind.PlaylistAdd -> {
                drawRoundRect(color, Offset(w * 0.16f, h * 0.24f), Size(w * 0.52f, h * 0.46f), CornerRadius(w * 0.07f, w * 0.07f), style = stroke)
                drawLine(color, Offset(w * 0.28f, h * 0.4f), Offset(w * 0.54f, h * 0.4f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.55f), Offset(w * 0.48f, h * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, h * 0.46f), Offset(w * 0.76f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.6f, h * 0.62f), Offset(w * 0.92f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Queue -> {
                val ys = listOf(0.28f, 0.5f, 0.72f)
                ys.forEachIndexed { index, y ->
                    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.18f, h * y))
                    drawLine(color, Offset(w * 0.3f, h * y), Offset(w * (if (index == 1) 0.78f else 0.88f), h * y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            EchoIconKind.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.34f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.5f)
                    lineTo(w * 0.34f, h * 0.78f)
                    close()
                }
                drawPath(path, color)
            }
            EchoIconKind.Pause -> {
                drawRoundRect(color, Offset(w * 0.32f, h * 0.24f), Size(w * 0.12f, h * 0.52f), CornerRadius(w * 0.04f, w * 0.04f))
                drawRoundRect(color, Offset(w * 0.56f, h * 0.24f), Size(w * 0.12f, h * 0.52f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            EchoIconKind.Previous, EchoIconKind.Next -> {
                val dir = if (kind == EchoIconKind.Next) 1f else -1f
                val center = w * 0.5f
                val path = Path().apply {
                    moveTo(center - dir * w * 0.2f, h * 0.24f)
                    lineTo(center + dir * w * 0.16f, h * 0.5f)
                    lineTo(center - dir * w * 0.2f, h * 0.76f)
                    close()
                }
                drawPath(path, color)
                drawLine(color, Offset(center + dir * w * 0.28f, h * 0.24f), Offset(center + dir * w * 0.28f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Heart -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.8f)
                    cubicTo(w * 0.18f, h * 0.58f, w * 0.16f, h * 0.28f, w * 0.36f, h * 0.25f)
                    cubicTo(w * 0.46f, h * 0.23f, w * 0.5f, h * 0.32f, w * 0.5f, h * 0.32f)
                    cubicTo(w * 0.5f, h * 0.32f, w * 0.54f, h * 0.23f, w * 0.64f, h * 0.25f)
                    cubicTo(w * 0.84f, h * 0.28f, w * 0.82f, h * 0.58f, w * 0.5f, h * 0.8f)
                    close()
                }
                drawPath(path, color)
            }
            EchoIconKind.Shuffle -> {
                drawLine(color, Offset(w * 0.18f, h * 0.3f), Offset(w * 0.45f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.45f, h * 0.3f), Offset(w * 0.72f, h * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.7f), Offset(w * 0.45f, h * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.45f, h * 0.7f), Offset(w * 0.72f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.3f), Offset(w * 0.84f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.7f), Offset(w * 0.84f, h * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Repeat -> {
                drawLine(color, Offset(w * 0.25f, h * 0.32f), Offset(w * 0.72f, h * 0.32f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.32f), Offset(w * 0.62f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.75f, h * 0.68f), Offset(w * 0.28f, h * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.68f), Offset(w * 0.38f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Network -> {
                val points = listOf(Offset(w * 0.28f, h * 0.32f), Offset(w * 0.72f, h * 0.28f), Offset(w * 0.52f, h * 0.72f))
                drawLine(color, points[0], points[1], strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, points[1], points[2], strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, points[2], points[0], strokeWidth = stroke.width, cap = StrokeCap.Round)
                points.forEach { drawCircle(color, radius = w * 0.09f, center = it) }
            }
            EchoIconKind.Settings -> {
                drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                for (i in 0 until 6) {
                    val angle = Math.PI * 2.0 * i / 6.0
                    val x1 = w * (0.5f + kotlin.math.cos(angle).toFloat() * 0.28f)
                    val y1 = h * (0.5f + kotlin.math.sin(angle).toFloat() * 0.28f)
                    val x2 = w * (0.5f + kotlin.math.cos(angle).toFloat() * 0.4f)
                    val y2 = h * (0.5f + kotlin.math.sin(angle).toFloat() * 0.4f)
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            EchoIconKind.Palette -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.38f, h * 0.36f))
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.58f, h * 0.34f))
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.66f, h * 0.52f))
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.46f, h * 0.66f), style = stroke)
            }
            EchoIconKind.Swatch -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.24f), Size(w * 0.32f, h * 0.5f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawRoundRect(color, Offset(w * 0.48f, h * 0.18f), Size(w * 0.32f, h * 0.5f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.58f, h * 0.78f))
            }
            EchoIconKind.Gauge -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.58f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.58f), Offset(w * 0.68f, h * 0.4f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.58f), Offset(w * 0.18f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.58f), Offset(w * 0.76f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Volume -> {
                val speaker = Path().apply {
                    moveTo(w * 0.2f, h * 0.42f)
                    lineTo(w * 0.36f, h * 0.42f)
                    lineTo(w * 0.54f, h * 0.28f)
                    lineTo(w * 0.54f, h * 0.72f)
                    lineTo(w * 0.36f, h * 0.58f)
                    lineTo(w * 0.2f, h * 0.58f)
                    close()
                }
                drawPath(speaker, color)
                drawLine(color, Offset(w * 0.64f, h * 0.38f), Offset(w * 0.74f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.74f, h * 0.5f), Offset(w * 0.64f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Timer -> {
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.55f), style = stroke)
                drawLine(color, Offset(w * 0.4f, h * 0.2f), Offset(w * 0.6f, h * 0.2f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.5f, h * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.64f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Lyrics -> {
                drawRoundRect(color, Offset(w * 0.18f, h * 0.22f), Size(w * 0.64f, h * 0.44f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.32f, h * 0.78f), Offset(w * 0.44f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.4f), Offset(w * 0.64f, h * 0.4f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.54f), Offset(w * 0.56f, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Back -> {
                drawLine(color, Offset(w * 0.72f, h * 0.22f), Offset(w * 0.34f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.72f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Artist -> {
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
                drawLine(color, Offset(w * 0.32f, h * 0.74f), Offset(w * 0.68f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.62f), Offset(w * 0.5f, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.62f), Offset(w * 0.5f, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Edit -> {
                drawLine(color, Offset(w * 0.26f, h * 0.72f), Offset(w * 0.68f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.6f, h * 0.22f), Offset(w * 0.76f, h * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.4f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Delete -> {
                drawLine(color, Offset(w * 0.28f, h * 0.34f), Offset(w * 0.72f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, h * 0.26f), Offset(w * 0.62f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.42f), Offset(w * 0.4f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.42f), Offset(w * 0.6f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Remove -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(color, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Import -> {
                drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.46f), Offset(w * 0.5f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, h * 0.46f), Offset(w * 0.5f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.24f, h * 0.72f), Size(w * 0.52f, h * 0.08f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            EchoIconKind.Folder -> {
                drawLine(color, Offset(w * 0.16f, h * 0.36f), Offset(w * 0.4f, h * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, h * 0.36f), Offset(w * 0.5f, h * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.84f, h * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.16f, h * 0.46f), Size(w * 0.68f, h * 0.34f), CornerRadius(w * 0.05f, w * 0.05f), style = stroke)
            }
            EchoIconKind.Check -> {
                drawLine(color, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.42f, h * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.7f), Offset(w * 0.78f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.ArrowUp, EchoIconKind.ArrowDown -> {
                val up = kind == EchoIconKind.ArrowUp
                val tipY = if (up) h * 0.22f else h * 0.78f
                val baseY = if (up) h * 0.48f else h * 0.52f
                drawLine(color, Offset(w * 0.5f, tipY), Offset(w * 0.5f, if (up) h * 0.78f else h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, tipY), Offset(w * 0.32f, baseY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, tipY), Offset(w * 0.68f, baseY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Sync -> {
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(color, Offset(w * 0.68f, h * 0.24f), Offset(w * 0.82f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, h * 0.42f), Offset(w * 0.82f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.76f), Offset(w * 0.18f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.58f), Offset(w * 0.18f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Action -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.7f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Refresh -> {
                // Circular arrow / refresh icon
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(color, Offset(w * 0.78f, h * 0.36f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.5f), Offset(w * 0.64f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.Language -> {
                // Globe: outer circle, equator, and a vertical meridian
                drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(color, Offset(w * 0.16f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawOval(color, Offset(w * 0.36f, h * 0.16f), Size(w * 0.28f, h * 0.68f), style = stroke)
            }
            EchoIconKind.ChevronRight -> {
                // Navigation chevron pointing right
                drawLine(color, Offset(w * 0.4f, h * 0.28f), Offset(w * 0.64f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.5f), Offset(w * 0.4f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EchoIconKind.More -> {
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.28f, h * 0.5f))
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.72f, h * 0.5f))
            }
            EchoIconKind.Sparkle -> {
                // Four-point sparkle / star for daily recommendations.
                val cx = w * 0.5f
                val cy = h * 0.5f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, h * 0.14f)
                    cubicTo(cx + w * 0.06f, cy - h * 0.06f, cx + w * 0.06f, cy - h * 0.06f, w * 0.86f, cy)
                    cubicTo(cx + w * 0.06f, cy + h * 0.06f, cx + w * 0.06f, cy + h * 0.06f, cx, h * 0.86f)
                    cubicTo(cx - w * 0.06f, cy + h * 0.06f, cx - w * 0.06f, cy + h * 0.06f, w * 0.14f, cy)
                    cubicTo(cx - w * 0.06f, cy - h * 0.06f, cx - w * 0.06f, cy - h * 0.06f, cx, h * 0.14f)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}
