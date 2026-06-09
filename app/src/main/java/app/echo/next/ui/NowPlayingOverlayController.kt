package app.echo.next.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R
import kotlin.math.max

/**
 * Immutable, primitive-only slices of [NowBarState] handed to the individual sections of the
 * full-screen player. Each slice carries only the fields that section reads, so when the
 * authoritative state is re-pushed every ~second the only slice whose `equals` changes is the
 * progress slice — Compose skips recomposing artwork, title, transport and mode controls.
 *
 * The cover is keyed on the URI string (a stable [String]) rather than the [android.net.Uri]
 * object so an identical cover across ticks does not look like a change to Compose.
 */
@Immutable
private data class ArtworkSlice(val artUriString: String?)

@Immutable
private data class TrackInfoSlice(
    val title: String,
    val subtitle: String
)

@Immutable
private data class TransportSlice(val playing: Boolean)

@Immutable
private data class ModeSlice(
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleEnabled: Boolean,
    val shuffleLabel: String,
    val repeatLabel: String,
    val repeatOffLabel: String
)

@Immutable
private data class ProgressSlice(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val elapsed: String,
    val duration: String
)

/**
 * Resident full-screen player. The [ComposeView] is created once (at shell build time) and kept in
 * the view tree, so opening the player never pays the Compose first-frame initialization cost. The
 * visible/hidden transition is driven by a Compose [visible] flag through [AnimatedVisibility]:
 *
 *  - [show] flips the host View to [View.VISIBLE] then sets `visible = true` → the enter animation
 *    (fade + slide-up) plays.
 *  - [hide] sets `visible = false` → the exit animation plays, after which the host View is set back
 *    to [View.GONE] so a hidden (but still MATCH_PARENT) overlay does not swallow touches meant for
 *    the content underneath.
 *
 * [isShowing] is read from the Java shell to route the back gesture.
 */
class NowPlayingOverlayController(
    context: Context,
    initialState: NowBarState,
    private val onDismiss: Runnable,
    private val onPrevious: Runnable,
    private val onPlayPause: Runnable,
    private val onNext: Runnable,
    private val onFavorite: Runnable,
    private val onShuffle: Runnable,
    private val onRepeat: Runnable,
    private val onQueue: Runnable,
    private val onSeek: SeekAction
) {
    private val state: MutableState<NowBarState> = mutableStateOf(initialState)
    private val visible: MutableState<Boolean> = mutableStateOf(false)

    val view: ComposeView = ComposeView(context).apply {
        visibility = View.GONE
        setContent {
            EchoTheme.EchoTheme {
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(durationMillis = 200)) +
                        slideInVertically(EchoMotion.layoutSpring<androidx.compose.ui.unit.IntOffset>().spec()) { fullHeight -> fullHeight / 5 },
                    exit = fadeOut(tween(durationMillis = 170)) +
                        slideOutVertically(tween(durationMillis = 220)) { fullHeight -> fullHeight / 6 }
                ) {
                    NowPlayingOverlay(
                        state = state.value,
                        onDismiss = onDismiss,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onFavorite = onFavorite,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        onQueue = onQueue,
                        onSeek = onSeek
                    )
                }
            }
        }
    }

    fun updateState(nextState: NowBarState) {
        state.value = nextState
    }

    fun isShowing(): Boolean = visible.value

    fun show() {
        view.visibility = View.VISIBLE
        visible.value = true
    }

    fun hide() {
        if (!visible.value) {
            return
        }
        visible.value = false
        // Keep the host View around for the exit animation, then hide it so it stops intercepting
        // touches. The guard re-checks visible in case the player was re-opened mid-exit.
        view.postDelayed({
            if (!visible.value) {
                view.visibility = View.GONE
            }
        }, EXIT_ANIMATION_MS)
    }

    private companion object {
        const val EXIT_ANIMATION_MS = 240L
    }
}

@Composable
private fun NowPlayingOverlay(
    state: NowBarState,
    onDismiss: Runnable,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable,
    onFavorite: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onQueue: Runnable,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        p.accentSoft.copy(alpha = 0.92f),
                        p.background,
                        p.backgroundDeep.copy(alpha = 0.98f)
                    )
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 820.dp
            val dense = maxHeight < 700.dp
            val topGap = when {
                dense -> 14.dp
                compact -> 22.dp
                else -> 46.dp
            }
            val artworkMax = when {
                dense -> 280.dp
                compact -> 320.dp
                else -> 430.dp
            }
            val artworkGap = when {
                dense -> 14.dp
                compact -> 20.dp
                else -> 38.dp
            }
            val titleGap = if (compact) 12.dp else 26.dp
            val transportGap = if (compact) 12.dp else 28.dp

            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(EchoIconKind.ArrowDown, "Close") { onDismiss.run() }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "正在播放",
                        style = EchoTypography.title,
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(topGap))
                ArtworkSection(
                    slice = ArtworkSlice(state.albumArtUri?.toString()),
                    title = state.title,
                    subtitle = state.subtitle,
                    artworkMax = artworkMax,
                    compact = compact
                )
                Spacer(Modifier.height(artworkGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackInfoSection(
                        slice = TrackInfoSlice(state.title, state.subtitle),
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    val modeSlice = ModeSlice(
                        favorite = state.favorite,
                        favoriteLabel = state.favoriteLabel,
                        favoritedLabel = state.favoritedLabel,
                        shuffleEnabled = state.shuffleEnabled,
                        shuffleLabel = state.shuffleLabel,
                        repeatLabel = state.repeatLabel,
                        repeatOffLabel = state.repeatOffLabel
                    )
                    CircleIconButton(
                        EchoIconKind.Heart,
                        if (modeSlice.favorite) modeSlice.favoritedLabel else modeSlice.favoriteLabel,
                        active = modeSlice.favorite
                    ) { onFavorite.run() }
                }
                Spacer(Modifier.height(titleGap))
                ProgressSection(
                    slice = ProgressSlice(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        playing = state.playing,
                        elapsed = state.elapsed,
                        duration = state.duration
                    ),
                    onSeek = onSeek
                )
                Spacer(Modifier.height(transportGap))
                TransportControls(
                    slice = TransportSlice(state.playing),
                    compact = compact,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext
                )
                Spacer(Modifier.weight(1f))
                ModeControls(
                    slice = ModeSlice(
                        favorite = state.favorite,
                        favoriteLabel = state.favoriteLabel,
                        favoritedLabel = state.favoritedLabel,
                        shuffleEnabled = state.shuffleEnabled,
                        shuffleLabel = state.shuffleLabel,
                        repeatLabel = state.repeatLabel,
                        repeatOffLabel = state.repeatOffLabel
                    ),
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onQueue = onQueue
                )
            }
        }
    }
}

@Composable
private fun ArtworkSection(
    slice: ArtworkSlice,
    title: String,
    subtitle: String,
    artworkMax: androidx.compose.ui.unit.Dp,
    compact: Boolean
) {
    val p = EchoTheme.colors()
    val uri = remember(slice.artUriString) { slice.artUriString?.let { android.net.Uri.parse(it) } }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AsyncArtwork(
            uri = uri,
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = artworkMax)
                .aspectRatio(1f)
                .clip(EchoShapes.full),
            cornerRadius = 16.dp,
            fallbackTextSize = if (compact) 46.sp else 54.sp,
            targetSize = 512.dp,
            backgroundColor = p.surfaceVariant,
            fallbackResId = R.drawable.ic_echo_launcher
        )
    }
}

@Composable
private fun TrackInfoSection(
    slice: TrackInfoSlice,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val titleSize = if (compact) 27.sp else 30.sp
    val titleLineHeight = if (compact) 33.sp else 36.sp
    val subtitleSize = if (compact) 16.sp else 18.sp
    val subtitleLineHeight = if (compact) 22.sp else 24.sp
    Column(modifier) {
        Text(
            slice.title,
            style = EchoTypography.display.copy(fontSize = titleSize, lineHeight = titleLineHeight),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (slice.subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                slice.subtitle,
                style = EchoTypography.body.copy(fontSize = subtitleSize, lineHeight = subtitleLineHeight),
                color = p.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressSection(
    slice: ProgressSlice,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    ScrubProgress(
        positionMs = slice.positionMs,
        durationMs = slice.durationMs,
        playing = slice.playing,
        onSeek = onSeek,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(slice.elapsed, style = EchoTypography.caption, color = p.muted)
        Text(slice.duration, style = EchoTypography.caption, color = p.muted)
    }
}

@Composable
private fun TransportControls(
    slice: TransportSlice,
    compact: Boolean,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable
) {
    val p = EchoTheme.colors()
    val playButtonSize = if (compact) 64.dp else 74.dp
    val playIconSize = if (compact) 31.dp else 36.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LargeTransportButton(EchoIconKind.Previous, "Previous") { onPrevious.run() }
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = { onPlayPause.run() },
            interactionSource = interaction,
            modifier = Modifier
                .size(playButtonSize)
                .echoPressScale(interaction)
                .semantics { contentDescription = if (slice.playing) "Pause" else "Play" },
            shape = CircleShape,
            color = p.accent
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Crossfade the play/pause glyph so the transport icon swaps softly.
                Crossfade(
                    targetState = slice.playing,
                    animationSpec = tween(durationMillis = EchoMotion.FAST_CROSSFADE_MS),
                    label = "playIcon"
                ) { playing ->
                    EchoIcon(
                        if (playing) EchoIconKind.Pause else EchoIconKind.Play,
                        Modifier.size(playIconSize),
                        p.onAccent
                    )
                }
            }
        }
        LargeTransportButton(EchoIconKind.Next, "Next") { onNext.run() }
    }
}

@Composable
private fun ModeControls(
    slice: ModeSlice,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onQueue: Runnable
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(EchoIconKind.Shuffle, slice.shuffleLabel, active = slice.shuffleEnabled) { onShuffle.run() }
        CircleIconButton(
            EchoIconKind.Repeat,
            slice.repeatLabel,
            active = slice.repeatLabel != slice.repeatOffLabel
        ) { onRepeat.run() }
        CircleIconButton(EchoIconKind.Queue, "Queue") { onQueue.run() }
    }
}

@Composable
private fun CircleIconButton(
    icon: EchoIconKind,
    desc: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (active) p.accentSoft.copy(alpha = 0.62f) else p.surfaceVariant.copy(alpha = 0.34f),
        animationSpec = EchoMotion.colorSpring(),
        label = "iconContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) p.accent else p.text,
        animationSpec = EchoMotion.colorSpring(),
        label = "iconContent"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(52.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = desc },
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(24.dp), contentColor)
        }
    }
}

@Composable
private fun LargeTransportButton(icon: EchoIconKind, desc: String, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(64.dp)
            .echoGlassLayer(p, CircleShape)
            .echoPressScale(interaction)
            .semantics { contentDescription = desc },
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(38.dp), p.text)
        }
    }
}

@Composable
private fun ScrubProgress(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
    // Locally-advancing position so the bar glides between the ~1s authoritative pushes. The draw
    // lambda reads this State, so the smooth motion only redraws the Canvas — it never recomposes.
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Playback progress" }
            .pointerInput(scrub.seekEnabled, scrub.duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!scrub.seekEnabled) {
                        return@awaitEachGesture
                    }
                    var targetPosition = scrub.scrubTo(down.position.x, size.width.toFloat())
                    val completed = drag(down.id) { change ->
                        targetPosition = scrub.scrubTo(change.position.x, size.width.toFloat())
                        change.consume()
                    }
                    if (completed) {
                        onSeek.seekTo(targetPosition)
                    }
                    scrub.clearScrub()
                }
            }
    ) {
        val progress = (scrub.displayPosition.value.toFloat() / scrub.duration.toFloat())
            .coerceIn(0f, 1f)
        val trackHeight = 10.dp.toPx()
        val centerY = size.height / 2f
        val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        drawRoundRect(
            color = p.surfaceVariant.copy(alpha = 0.82f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = p.onAccent.copy(alpha = 0.92f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * progress, trackHeight),
            cornerRadius = radius
        )
        val thumbWidth = max(5.dp.toPx(), trackHeight * 0.56f)
        val thumbHeight = 42.dp.toPx()
        val thumbX = (size.width * progress).coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
        drawRoundRect(
            color = p.text,
            topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
        )
    }
}
