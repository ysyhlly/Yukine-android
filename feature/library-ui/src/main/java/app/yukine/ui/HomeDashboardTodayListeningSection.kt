package app.yukine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val GLYPH_G_CLEF = "\uE050"
private const val GLYPH_NOTE_QUARTER_UP = "\uE1D5"
private const val GLYPH_NOTE_EIGHTH_UP = "\uE1D7"
private const val GLYPH_NOTE_HALF_UP = "\uE1D3"
private const val GLYPH_REST_QUARTER = "\uE4E5"
private const val GLYPH_AUGMENTATION_DOT = "\uE1E7"
private const val GLYPH_ACCIDENTAL_FLAT = "\uE260"
private const val GLYPH_ACCIDENTAL_NATURAL = "\uE261"
private const val GLYPH_ACCIDENTAL_SHARP = "\uE262"

private val BravuraFontFamily = FontFamily(Font(R.font.bravura))

@Composable
internal fun TodayListeningSection(state: HomeDashboardUiState) {
    val p = EchoTheme.colors()
    val hasListening = state.todayListeningPoints.any { !it.future && it.durationMs > 0L }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("今日聆听轨迹", style = EchoTypography.title, color = p.text)
            Text(
                if (hasListening) "留下一段回声" else "尚未响起",
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (hasListening) p.accent else p.muted
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGaussianBackdrop(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = echoCardColor(p.surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (hasListening) "今天听过的，都落进了旋律里" else "等待今天的第一声回响",
                    style = EchoTypography.caption,
                    color = p.muted
                )
                TodayListeningScore(
                    points = state.todayListeningPoints,
                    durationLabel = state.todayListeningDuration
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("清晨", "正午", "黄昏", "深夜").forEach { label ->
                        Text(
                            label,
                            style = EchoTypography.small.copy(fontSize = 9.sp),
                            color = p.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayListeningScore(
    points: List<HomeDashboardListeningPoint>,
    durationLabel: String
) {
    val p = EchoTheme.colors()
    val hourlyIntensity = normalizedHourlyIntensity(points)
    val events = odeToJoyClimax()
    val chartHeight = 116.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { contentDescription = "今日聆听轨迹，$durationLabel" }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { chartHeight.toPx() }
        val staffTopPx = heightPx * 0.29f
        val staffBottomPx = heightPx * 0.73f
        val staffCenterPx = (staffTopPx + staffBottomPx) / 2f
        val staffGapPx = (staffBottomPx - staffTopPx) / 4f
        val noteCenterPx = staffCenterPx - staffGapPx * 0.52f
        val scoreStartPx = widthPx * 0.085f
        val scoreEndPx = widthPx * 0.975f

        fun centerFor(index: Int, pitch: Int): Offset {
            val progress = index / events.lastIndex.toFloat()
            return Offset(
                x = scoreStartPx + (scoreEndPx - scoreStartPx) * progress,
                y = noteCenterPx - pitch * staffGapPx * 0.28f
            )
        }

        repeat(5) { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = with(density) { (staffTopPx + staffGapPx * line).toDp() })
                    .height(1.dp)
                    .background(p.border.copy(alpha = 0.52f))
            )
        }

        ScoreGlyph(
            glyph = GLYPH_G_CLEF,
            x = 0.dp,
            y = with(density) { (staffTopPx - staffGapPx * 1.85f).toDp() },
            size = 25.dp,
            color = p.muted.copy(alpha = 0.62f)
        )

        events.forEachIndexed { index, event ->
            val center = centerFor(index, event.pitch)
            val hour = 23f * index / events.lastIndex.toFloat()
            val intensity = listeningEnvelopeAt(hour, hourlyIntensity)
            val displayIntensity = listeningDisplayIntensity(intensity)
            val pulse = compositePulseAt(hour)
            val strength = displayIntensity * (0.74f + pulse * 0.26f)
            val active = strength >= event.activationGate
            val lowActivityRest = intensity in 0.001f..0.20f && index % 4 == 3

            ScoreGlyphAt(
                glyph = glyphForRole(event.role),
                center = center,
                size = when (event.role) {
                    ScoreRole.Sustained -> 23.dp
                    ScoreRole.Eighth, ScoreRole.Arpeggio -> 20.dp
                    else -> 21.dp
                },
                color = p.muted.copy(alpha = 0.22f)
            )
            if (event.role == ScoreRole.Dotted) {
                ScoreGlyphAt(
                    glyph = GLYPH_AUGMENTATION_DOT,
                    center = center.copy(
                        x = center.x + with(density) { 7.dp.toPx() },
                        y = center.y - with(density) { 1.dp.toPx() }
                    ),
                    size = 12.dp,
                    color = p.muted.copy(alpha = 0.22f),
                    verticalAnchor = 0.78f
                )
            }
            if (event.role == ScoreRole.Chord) {
                ScoreGlyphAt(
                    glyph = GLYPH_NOTE_QUARTER_UP,
                    center = center.copy(y = center.y - staffGapPx * 0.58f),
                    size = 21.dp,
                    color = p.muted.copy(alpha = 0.16f)
                )
            }

            if (active || lowActivityRest) {
                val activeColor = p.accent.copy(alpha = 0.30f + displayIntensity * 0.70f)
                if (lowActivityRest) {
                    ScoreGlyphAt(
                        glyph = GLYPH_REST_QUARTER,
                        center = center.copy(y = staffCenterPx),
                        size = 17.dp,
                        color = activeColor,
                        verticalAnchor = 0.72f
                    )
                } else {
                    val activeSize = if (
                        event.role == ScoreRole.Eighth || event.role == ScoreRole.Arpeggio
                    ) {
                        22.dp
                    } else {
                        23.dp
                    }
                    ScoreGlyphAt(glyphForRole(event.role), center, activeSize, activeColor)

                    if (event.accidental != null) {
                        ScoreGlyphAt(
                            glyph = event.accidental,
                            center = center.copy(x = center.x - with(density) { 8.dp.toPx() }),
                            size = 13.dp,
                            color = activeColor,
                            verticalAnchor = 0.78f
                        )
                    }
                    if (event.role == ScoreRole.Dotted && intensity >= 0.34f) {
                        ScoreGlyphAt(
                            glyph = GLYPH_AUGMENTATION_DOT,
                            center = center.copy(
                                x = center.x + with(density) { 7.dp.toPx() },
                                y = center.y - with(density) { 1.dp.toPx() }
                            ),
                            size = 12.dp,
                            color = activeColor,
                            verticalAnchor = 0.78f
                        )
                    }
                    if (event.role == ScoreRole.Chord && intensity >= 0.62f) {
                        ScoreGlyphAt(
                            glyph = GLYPH_NOTE_QUARTER_UP,
                            center = center.copy(y = center.y - staffGapPx * 0.58f),
                            size = 23.dp,
                            color = activeColor
                        )
                        if (intensity >= 0.90f) {
                            ScoreGlyphAt(
                                glyph = GLYPH_NOTE_QUARTER_UP,
                                center = center.copy(y = center.y + staffGapPx * 0.58f),
                                size = 23.dp,
                                color = activeColor
                            )
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val connectorColor = p.accent.copy(alpha = 0.62f)
            val mutedConnector = p.muted.copy(alpha = 0.20f)

            listOf(3, 7, 11, 15).forEach { boundary ->
                val left = centerFor(boundary, events[boundary].pitch).x
                val right = centerFor(boundary + 1, events[boundary + 1].pitch).x
                val x = (left + right) / 2f
                drawLine(
                    color = mutedConnector,
                    start = Offset(x, staffTopPx),
                    end = Offset(x, staffBottomPx),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            drawLine(
                color = mutedConnector,
                start = Offset(scoreEndPx + 3.dp.toPx(), staffTopPx),
                end = Offset(scoreEndPx + 3.dp.toPx(), staffBottomPx),
                strokeWidth = 2.dp.toPx()
            )

            listOf(5 to 7, 12 to 15).forEach { (startIndex, endIndex) ->
                val startHour = 23f * startIndex / events.lastIndex.toFloat()
                if (listeningEnvelopeAt(startHour, hourlyIntensity) >= 0.48f) {
                    val start = centerFor(startIndex, events[startIndex].pitch)
                    val end = centerFor(endIndex, events[endIndex].pitch)
                    val slurY = minOf(
                        staffBottomPx - staffGapPx * 0.68f,
                        maxOf(start.y, end.y) + staffGapPx * 0.36f
                    )
                    val slur = Path().apply {
                        moveTo(start.x, slurY)
                        cubicTo(
                            start.x + (end.x - start.x) * 0.28f,
                            slurY + staffGapPx * 0.14f,
                            start.x + (end.x - start.x) * 0.72f,
                            slurY + staffGapPx * 0.14f,
                            end.x,
                            slurY
                        )
                    }
                    drawPath(
                        path = slur,
                        color = connectorColor,
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            val heldStart = centerFor(events.lastIndex - 1, events[events.lastIndex - 1].pitch)
            val heldEnd = centerFor(events.lastIndex, events.last().pitch)
            val tieY = staffBottomPx - staffGapPx * 0.68f
            val tie = Path().apply {
                moveTo(heldStart.x, tieY)
                cubicTo(
                    heldStart.x + (heldEnd.x - heldStart.x) * 0.32f,
                    tieY + staffGapPx * 0.14f,
                    heldStart.x + (heldEnd.x - heldStart.x) * 0.68f,
                    tieY + staffGapPx * 0.14f,
                    heldEnd.x,
                    tieY
                )
            }
            drawPath(
                path = tie,
                color = mutedConnector,
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ScoreGlyphAt(
    glyph: String,
    center: Offset,
    size: Dp,
    color: Color,
    verticalAnchor: Float = 1.32f
) {
    val density = LocalDensity.current
    ScoreGlyph(
        glyph = glyph,
        x = with(density) { center.x.toDp() } - size / 2,
        y = with(density) { center.y.toDp() } - size * verticalAnchor,
        size = size,
        color = color
    )
}

@Composable
private fun ScoreGlyph(
    glyph: String,
    x: Dp,
    y: Dp,
    size: Dp,
    color: Color
) {
    Text(
        text = glyph,
        modifier = Modifier.offset(x = x, y = y),
        color = color,
        fontFamily = BravuraFontFamily,
        fontSize = size.value.sp,
        lineHeight = size.value.sp
    )
}

private fun normalizedHourlyIntensity(
    points: List<HomeDashboardListeningPoint>
): List<Float> {
    val stablePoints = if (points.size == 24) {
        points
    } else {
        List(24) { hour ->
            points.firstOrNull { it.hour == hour } ?: HomeDashboardListeningPoint(hour, 0L)
        }
    }
    val maxDurationPerHourMs = 60f * 60f * 1_000f
    return stablePoints.map { point ->
        val duration = if (point.future) 0L else point.durationMs
        (duration / maxDurationPerHourMs).coerceIn(0f, 1f)
    }
}

private fun listeningEnvelopeAt(hour: Float, hourlyIntensity: List<Float>): Float {
    val segment = hour.toInt().coerceIn(0, hourlyIntensity.lastIndex)
    val progress = (hour - segment).coerceIn(0f, 1f)
    val progress2 = progress * progress
    val progress3 = progress2 * progress
    fun intensityAt(index: Int): Float =
        hourlyIntensity[index.coerceIn(0, hourlyIntensity.lastIndex)]

    val p0 = intensityAt(segment - 1)
    val p1 = intensityAt(segment)
    val p2 = intensityAt(segment + 1)
    val p3 = intensityAt(segment + 2)
    val smoothed =
        ((1f - progress).let { it * it * it } * p0 +
            (3f * progress3 - 6f * progress2 + 4f) * p1 +
            (-3f * progress3 + 3f * progress2 + 3f * progress + 1f) * p2 +
            progress3 * p3) / 6f
    return sqrt(smoothed.coerceIn(0f, 1f))
}

private fun listeningDisplayIntensity(intensity: Float): Float {
    if (intensity <= 0.001f) return 0f
    return (0.22f + intensity * 0.78f).coerceIn(0f, 1f)
}

private fun compositePulseAt(hour: Float): Float {
    val phase = hour / 3f * PI.toFloat()
    val composite =
        sin(phase) * 0.55f +
            sin(phase * 2f + PI.toFloat() / 3f) * 0.30f +
            sin(phase * 3f - PI.toFloat() / 5f) * 0.15f
    return abs(composite).coerceIn(0f, 1f)
}

private fun odeToJoyClimax(): List<ScoreEvent> = listOf(
    ScoreEvent(0, ScoreRole.Eighth, activationGate = 0.08f),
    ScoreEvent(0, ScoreRole.Eighth, activationGate = 0.18f),
    ScoreEvent(1, ScoreRole.Eighth, activationGate = 0.28f),
    ScoreEvent(2, ScoreRole.Eighth, activationGate = 0.38f),
    ScoreEvent(2, ScoreRole.Quarter, activationGate = 0.10f),
    ScoreEvent(1, ScoreRole.Dotted, activationGate = 0.26f),
    ScoreEvent(0, ScoreRole.Eighth, activationGate = 0.18f),
    ScoreEvent(-1, ScoreRole.Eighth, activationGate = 0.34f),
    ScoreEvent(
        -2,
        ScoreRole.Eighth,
        accidental = GLYPH_ACCIDENTAL_NATURAL,
        activationGate = 0.12f
    ),
    ScoreEvent(-2, ScoreRole.Eighth, activationGate = 0.30f),
    ScoreEvent(-1, ScoreRole.Dotted, activationGate = 0.38f),
    ScoreEvent(0, ScoreRole.Chord, activationGate = 0.46f),
    ScoreEvent(0, ScoreRole.Arpeggio, activationGate = 0.22f),
    ScoreEvent(-1, ScoreRole.Arpeggio, activationGate = 0.36f),
    ScoreEvent(-1, ScoreRole.Chord, activationGate = 0.54f),
    ScoreEvent(0, ScoreRole.Eighth, activationGate = 0.24f),
    ScoreEvent(-1, ScoreRole.Eighth, activationGate = 0.34f),
    ScoreEvent(-2, ScoreRole.Sustained, activationGate = 0.08f)
)

private data class ScoreEvent(
    val pitch: Int,
    val role: ScoreRole,
    val accidental: String? = null,
    val activationGate: Float
)

private fun glyphForRole(role: ScoreRole): String = when (role) {
    ScoreRole.Eighth, ScoreRole.Arpeggio -> GLYPH_NOTE_EIGHTH_UP
    ScoreRole.Sustained -> GLYPH_NOTE_HALF_UP
    else -> GLYPH_NOTE_QUARTER_UP
}

private enum class ScoreRole {
    Eighth,
    Quarter,
    Dotted,
    Chord,
    Arpeggio,
    Sustained
}
