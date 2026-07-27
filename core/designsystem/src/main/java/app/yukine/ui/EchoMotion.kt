package app.yukine.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared motion vocabulary for Yukine. Keeping every animation spec in one place means the
 * whole app shares a coherent feel and the timing/physics can be tuned from a single file.
 *
 * Springs are preferred for anything the user directly drives (presses, toggles, list movement)
 * because spring motion settles naturally and reacts to interruption, which feels more "丝滑"
 * (silky) than a fixed-duration tween. Tweens are kept for content cross-fades where a precise,
 * predictable duration reads better than physical overshoot.
 */
object EchoMotion {
    /** Snappy press/scale feedback — low visual bounce, quick settle. */
    fun <T> pressSpring(): SpringFactory<T> = SpringFactory(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Standard spring for color / size state changes that should feel lively but controlled. */
    fun <T> standardSpring(): SpringFactory<T> = SpringFactory(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Gentle spring for larger layout movement (list item placement, enter/exit offsets). */
    fun <T> layoutSpring(): SpringFactory<T> = SpringFactory(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow
    )

    /** Color transitions — spring keeps active/inactive tint changes feeling alive. */
    fun colorSpring(): AnimationSpec<Color> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    fun floatSpring(): AnimationSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    /** Cross-fade timing for swapping content (icons, artwork). */
    const val CROSSFADE_MS = 180
    const val FAST_CROSSFADE_MS = 140

    /** Breathing / skeleton pulse half-cycle (full cycle is 2x). */
    const val BREATH_MS = 900

    /** Confirm pulse peak scale for favorite / toggle confirmation. */
    const val CONFIRM_PULSE_SCALE = 1.16f

    /**
     * Divisor for light horizontal page slides: offset = fullWidth / [PAGE_SLIDE_DIVISOR].
     * Pure function [horizontalSlideOffset] is the single source used by route transitions.
     */
    const val PAGE_SLIDE_DIVISOR = 24

    fun fade(): FiniteAnimationSpec<Float> = tween(CROSSFADE_MS)

    /** Press-scale target used consistently across all tappable controls. */
    const val PRESS_SCALE = 0.93f

    /** Item enter durations for staggered list appearance. */
    const val ITEM_ENTER_MS = 260
    const val ITEM_FADE_MS = 220

    /**
     * Light horizontal slide distance for route/page content swaps.
     * Keeps transitions subtle (about 4% of width) so they read as polish, not travel.
     */
    @JvmStatic
    fun horizontalSlideOffset(fullWidth: Int, divisor: Int = PAGE_SLIDE_DIVISOR): Int {
        if (fullWidth <= 0 || divisor <= 0) return 0
        return fullWidth / divisor
    }

    /** Shared enter/exit for out-of-pager routes and settings subpages. */
    fun pageContentTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(CROSSFADE_MS, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(CROSSFADE_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> horizontalSlideOffset(fullWidth) }
            )
        val exit = fadeOut(animationSpec = tween(FAST_CROSSFADE_MS, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(FAST_CROSSFADE_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> -horizontalSlideOffset(fullWidth) }
            )
        return enter togetherWith exit
    }

    /** Track-title / metadata swap when identity changes — vertical-light + fade. */
    fun trackContentTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(CROSSFADE_MS))
        val exit = fadeOut(animationSpec = tween(FAST_CROSSFADE_MS))
        return enter togetherWith exit
    }

    class SpringFactory<T>(
        private val dampingRatio: Float,
        private val stiffness: Float
    ) {
        fun spec(): FiniteAnimationSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }
}

/**
 * Adds a spring-driven press-scale to any clickable [Modifier]. Reads the press state from the
 * supplied [interactionSource] (so it stays in sync with the control's own ripple/click handling)
 * and only animates a [scale] — cheap, and it never recomposes the content.
 */
@Composable
fun Modifier.echoPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = EchoMotion.PRESS_SCALE
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = EchoMotion.floatSpring(),
        label = "echoPressScale"
    )
    this.scale(scale)
}

/**
 * One-shot entrance animation for a section/card: fades in while easing up from a small vertical
 * offset. [index] staggers multiple siblings so a screen's sections cascade in rather than all
 * snapping at once. The animation runs once per composition (keyed by [index]); it does not replay
 * on recomposition, so list scrolling stays cheap.
 *
 * Implemented with [graphicsLayer] (alpha + translationY) so it only affects the draw/layer phase,
 * never triggering layout of the content.
 */
@Composable
fun Modifier.echoEnter(
    index: Int = 0,
    risePx: Float = 36f,
    staggerMs: Int = 45
): Modifier = composed {
    val progress = remember(index) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(index) {
        kotlinx.coroutines.delay((index.coerceAtLeast(0) * staggerMs).toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EchoMotion.ITEM_ENTER_MS)
        )
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = risePx * (1f - progress.value)
    }
}

/**
 * Soft alpha pulse for empty/loading surfaces. Draw-phase only; disable with [enabled]=false
 * so idle content stays solid.
 */
@Composable
fun Modifier.echoBreath(
    enabled: Boolean = true,
    minAlpha: Float = 0.55f,
    maxAlpha: Float = 0.95f
): Modifier = composed {
    if (!enabled) {
        return@composed this
    }
    val transition = rememberInfiniteTransition(label = "echoBreath")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = EchoMotion.BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "echoBreathAlpha"
    )
    this.graphicsLayer { this.alpha = alpha }
}

/**
 * One-shot confirmation scale when [trigger] changes (e.g. favorite toggled). Skips the first
 * composition so initial state does not pulse on enter.
 */
@Composable
fun Modifier.echoConfirmPulse(
    trigger: Any?,
    peakScale: Float = EchoMotion.CONFIRM_PULSE_SCALE
): Modifier = composed {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (!armed) {
            armed = true
            return@LaunchedEffect
        }
        scale.snapTo(1f)
        scale.animateTo(
            targetValue = peakScale,
            animationSpec = EchoMotion.pressSpring<Float>().spec()
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = EchoMotion.floatSpring()
        )
    }
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
