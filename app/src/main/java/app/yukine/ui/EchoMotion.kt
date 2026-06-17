package app.yukine.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared motion vocabulary for ECHO Next. Keeping every animation spec in one place means the
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

    fun <T> fade(): FiniteAnimationSpec<Float> = tween(CROSSFADE_MS)

    /** Press-scale target used consistently across all tappable controls. */
    const val PRESS_SCALE = 0.93f

    /** Item enter durations for staggered list appearance. */
    const val ITEM_ENTER_MS = 260
    const val ITEM_FADE_MS = 220

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

