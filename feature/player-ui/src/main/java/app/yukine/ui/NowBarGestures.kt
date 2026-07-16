package app.yukine.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val WAVEFORM_TWO_PI = 6.2831855f

internal fun Modifier.playbackSeekGesture(
    scrub: ScrubbablePlaybackPosition,
    onSeek: SeekAction
): Modifier = pointerInput(scrub.seekEnabled, scrub.duration) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!scrub.seekEnabled) {
            return@awaitEachGesture
        }
        var targetPosition = scrub.scrubTo(down.position.x, size.width.toFloat())
        try {
            val completed = drag(down.id) { change ->
                targetPosition = scrub.scrubTo(change.position.x, size.width.toFloat())
                change.consume()
            }
            if (completed) {
                onSeek.seekTo(targetPosition)
            }
        } finally {
            scrub.clearScrub()
        }
    }
}

internal fun Modifier.nowBarDockGesture(
    enabled: Boolean,
    dockPosition: NowBarDockPosition = NowBarDockPosition.Expanded,
    onDockLeft: () -> Unit,
    onDockRight: () -> Unit,
    onDockTop: () -> Unit = {},
    onRestoreBottom: () -> Unit = {},
    onCompactTopCloud: () -> Unit = {},
    onTap: () -> Unit = {}
): Modifier = composed {
    if (!enabled) return@composed this
    val density = LocalDensity.current
    val distanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarDockSwipeDistance.toPx()
    }
    val velocityThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarDockSwipeVelocityDpPerSecond.dp.toPx()
    }
    val topCloudEnterDistanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudEnterDistance.toPx()
    }
    val topCloudVelocityThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudSwipeVelocityDpPerSecond.dp.toPx()
    }
    val restoreDistanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudRestoreDistance.toPx()
    }
    pointerInput(
        dockPosition,
        onDockLeft,
        onDockRight,
        onDockTop,
        onRestoreBottom,
        onCompactTopCloud,
        onTap,
        distanceThresholdPx,
        topCloudEnterDistanceThresholdPx,
        restoreDistanceThresholdPx,
        velocityThresholdPx,
        topCloudVelocityThresholdPx
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var deltaX = 0f
            var deltaY = 0f
            var gestureAxis = 0

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                deltaX = change.position.x - down.position.x
                deltaY = change.position.y - down.position.y

                if (gestureAxis == 0) {
                    val topCloudDiagonalDown =
                        (dockPosition == NowBarDockPosition.TopCloud ||
                            dockPosition == NowBarDockPosition.TopCloudExpanded) &&
                            deltaY > viewConfiguration.touchSlop &&
                            abs(deltaY) > abs(deltaX) / EchoMobileLayoutMetrics.nowBarDockHorizontalRatio
                    gestureAxis = when {
                        abs(deltaX) > viewConfiguration.touchSlop &&
                            abs(deltaX) > EchoMobileLayoutMetrics.nowBarDockHorizontalRatio * abs(deltaY) -> 1
                        topCloudDiagonalDown -> 2
                        abs(deltaY) > viewConfiguration.touchSlop &&
                            abs(deltaY) > EchoMobileLayoutMetrics.nowBarDockVerticalRatio * abs(deltaX) -> 2
                        else -> 0
                    }
                }
                if (gestureAxis != 0) change.consume()
                if (!change.pressed) {
                    val velocity = velocityTracker.calculateVelocity()
                    val horizontalDominant =
                        abs(deltaX) > EchoMobileLayoutMetrics.nowBarDockHorizontalRatio * abs(deltaY)
                    val verticalDominant =
                        abs(deltaY) > EchoMobileLayoutMetrics.nowBarDockVerticalRatio * abs(deltaX)
                    val horizontalThreshold =
                        abs(deltaX) >= distanceThresholdPx || abs(velocity.x) >= velocityThresholdPx
                    if (dockPosition != NowBarDockPosition.TopCloud &&
                        dockPosition != NowBarDockPosition.TopCloudExpanded &&
                        horizontalDominant && horizontalThreshold
                    ) {
                        val direction = if (abs(deltaX) >= distanceThresholdPx) deltaX else velocity.x
                        if (direction > 0f) onDockRight() else onDockLeft()
                    } else if (gestureAxis == 2 || verticalDominant) {
                        val direction = if (abs(deltaY) > viewConfiguration.touchSlop) {
                            deltaY
                        } else {
                            velocity.y
                        }
                        val verticalDistance = if (
                            (dockPosition == NowBarDockPosition.TopCloud ||
                                dockPosition == NowBarDockPosition.TopCloudExpanded) && direction > 0f
                        ) {
                            restoreDistanceThresholdPx
                        } else {
                            topCloudEnterDistanceThresholdPx
                        }
                        val verticalThreshold =
                            abs(deltaY) >= verticalDistance ||
                                abs(velocity.y) >= topCloudVelocityThresholdPx
                        if (verticalThreshold) {
                            val resolvedDirection = if (abs(deltaY) >= verticalDistance) deltaY else velocity.y
                            when {
                                dockPosition == NowBarDockPosition.Expanded && resolvedDirection < 0f ->
                                    onDockTop()
                                (dockPosition == NowBarDockPosition.TopCloud ||
                                    dockPosition == NowBarDockPosition.TopCloudExpanded) && resolvedDirection > 0f -> {
                                    val diagonalThreshold = abs(deltaY) *
                                        EchoMobileLayoutMetrics.nowBarTopCloudDiagonalDockRatio
                                    when {
                                        deltaX <= -diagonalThreshold -> onDockLeft()
                                        deltaX >= diagonalThreshold -> onDockRight()
                                        else -> onRestoreBottom()
                                    }
                                }
                                dockPosition == NowBarDockPosition.TopCloudExpanded && resolvedDirection < 0f ->
                                    onCompactTopCloud()
                                (dockPosition == NowBarDockPosition.BottomLeft ||
                                    dockPosition == NowBarDockPosition.BottomRight) && resolvedDirection < 0f ->
                                    onDockTop()
                            }
                        }
                    } else if (gestureAxis == 0 &&
                        abs(deltaX) <= viewConfiguration.touchSlop &&
                        abs(deltaY) <= viewConfiguration.touchSlop
                    ) {
                        onTap()
                    }
                    break
                }
            }
        }
    }
}
