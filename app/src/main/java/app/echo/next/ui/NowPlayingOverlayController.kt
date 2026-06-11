package app.echo.next.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R

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
private data class LyricsSlice(
    val title: String,
    val status: String,
    val showArtworkLabel: String,
    val noLyricsFoundLabel: String,
    val lines: List<LyricUiLine>
)

@Immutable
private data class TrackInfoSlice(
    val title: String,
    val subtitle: String
)

@Immutable
private data class TransportSlice(
    val playing: Boolean,
    val previousLabel: String,
    val playLabel: String,
    val pauseLabel: String,
    val nextLabel: String
)

@Immutable
private data class ModeSlice(
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleEnabled: Boolean,
    val shuffleLabel: String,
    val repeatLabel: String,
    val repeatOffLabel: String,
    val queueLabel: String
)

@Immutable
private data class MoreMenuSlice(
    val moreLabel: String,
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val addToPlaylistLabel: String,
    val queueLabel: String,
    val toggleViewLabel: String
)

@Immutable
private data class ProgressSlice(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val elapsed: String,
    val duration: String,
    val playbackProgressLabel: String
)

@Immutable
private data class PlaybackErrorSlice(
    val title: String,
    val message: String,
    val retryLabel: String
) {
    val visible: Boolean get() = message.isNotBlank()
}

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
    private val onAddToPlaylist: Runnable,
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
                        onAddToPlaylist = onAddToPlaylist,
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
    onAddToPlaylist: Runnable,
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
                        p.accentSoft.copy(alpha = 0.34f),
                        p.background,
                        p.background
                    )
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 26.dp, vertical = 18.dp)
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
            var showingLyrics by remember(state.trackId) { mutableStateOf(false) }

            if (showingLyrics) {
                LyricsOverlayPage(
                    state = state,
                    compact = compact,
                    onDismiss = onDismiss,
                    onToggle = { showingLyrics = false },
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onFavorite = onFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onQueue = onQueue,
                    onSeek = onSeek
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(EchoIconKind.ArrowDown, state.closeLabel) { onDismiss.run() }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        state.nowPlayingLabel,
                        style = EchoTypography.title,
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    NowPlayingMoreMenu(
                        slice = MoreMenuSlice(
                            moreLabel = state.moreLabel,
                            favorite = state.favorite,
                            favoriteLabel = state.favoriteLabel,
                            favoritedLabel = state.favoritedLabel,
                            addToPlaylistLabel = state.addToPlaylistLabel,
                            queueLabel = state.queueLabel,
                            toggleViewLabel = state.showLyricsLabel
                        ),
                        toggleIcon = EchoIconKind.Lyrics,
                        onFavorite = onFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                        onQueue = onQueue,
                        onToggleView = { showingLyrics = true }
                    )
                }
                Spacer(Modifier.height(topGap))
                ArtworkSection(
                    slice = ArtworkSlice(state.albumArtUri?.toString()),
                    title = state.title,
                    subtitle = state.subtitle,
                    showLyricsLabel = state.showLyricsLabel,
                    artworkMax = artworkMax,
                    compact = compact,
                    onToggle = { showingLyrics = true }
                )
                Spacer(Modifier.height(artworkGap))
                TrackInfoSection(
                    slice = TrackInfoSlice(state.title, state.subtitle),
                    compact = compact,
                    centered = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(titleGap))
                PlaybackErrorBanner(
                    slice = PlaybackErrorSlice(
                        title = state.playbackErrorTitle,
                        message = state.playbackErrorMessage,
                        retryLabel = state.retryLabel
                    ),
                    compact = compact,
                    onRetry = { onPlayPause.run() }
                )
                ProgressSection(
                    slice = ProgressSlice(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        playing = state.playing,
                        elapsed = state.elapsed,
                        duration = state.duration,
                        playbackProgressLabel = state.playbackProgressLabel
                    ),
                    onSeek = onSeek
                )
                Spacer(Modifier.height(transportGap))
                TransportControls(
                    slice = TransportSlice(
                        playing = state.playing,
                        previousLabel = state.previousLabel,
                        playLabel = state.playLabel,
                        pauseLabel = state.pauseLabel,
                        nextLabel = state.nextLabel
                    ),
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
                        repeatOffLabel = state.repeatOffLabel,
                        queueLabel = state.queueLabel
                    ),
                    onFavorite = onFavorite,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onQueue = onQueue
                )
                }
            }
        }
    }
}

@Composable
private fun LyricsOverlayPage(
    state: NowBarState,
    compact: Boolean,
    onDismiss: Runnable,
    onToggle: () -> Unit,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable,
    onFavorite: Runnable,
    onAddToPlaylist: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onQueue: Runnable,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    val topGap = if (compact) 26.dp else 42.dp
    val controlGap = if (compact) 12.dp else 24.dp
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(EchoIconKind.ArrowDown, state.closeLabel) { onDismiss.run() }
            Spacer(Modifier.width(16.dp))
            Text(
                state.lyricsTitle,
                style = EchoTypography.title,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NowPlayingMoreMenu(
                slice = MoreMenuSlice(
                    moreLabel = state.moreLabel,
                    favorite = state.favorite,
                    favoriteLabel = state.favoriteLabel,
                    favoritedLabel = state.favoritedLabel,
                    addToPlaylistLabel = state.addToPlaylistLabel,
                    queueLabel = state.queueLabel,
                    toggleViewLabel = state.showArtworkLabel
                ),
                toggleIcon = EchoIconKind.Mark,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onQueue = onQueue,
                onToggleView = onToggle
            )
        }
        Spacer(Modifier.height(topGap))
        Text(
            state.title,
            style = EchoTypography.display.copy(
                fontSize = if (compact) 26.sp else 30.sp,
                lineHeight = if (compact) 32.sp else 36.sp
            ),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.subtitle,
                style = EchoTypography.body,
                color = p.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(if (compact) 22.dp else 38.dp))
        LyricsStage(
            slice = LyricsSlice(
                title = state.lyricsTitle,
                status = state.lyricsStatus,
                showArtworkLabel = state.showArtworkLabel,
                noLyricsFoundLabel = state.noLyricsFoundLabel,
                lines = state.lyrics
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onToggle = onToggle
        )
        Spacer(Modifier.height(controlGap))
        PlaybackErrorBanner(
            slice = PlaybackErrorSlice(
                title = state.playbackErrorTitle,
                message = state.playbackErrorMessage,
                retryLabel = state.retryLabel
            ),
            compact = compact,
            onRetry = { onPlayPause.run() }
        )
        ProgressSection(
            slice = ProgressSlice(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playing = state.playing,
                elapsed = state.elapsed,
                duration = state.duration,
                playbackProgressLabel = state.playbackProgressLabel
            ),
            onSeek = onSeek
        )
        Spacer(Modifier.height(controlGap))
        TransportControls(
            slice = TransportSlice(
                playing = state.playing,
                previousLabel = state.previousLabel,
                playLabel = state.playLabel,
                pauseLabel = state.pauseLabel,
                nextLabel = state.nextLabel
            ),
            compact = compact,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext
        )
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        ModeControls(
            slice = ModeSlice(
                favorite = state.favorite,
                favoriteLabel = state.favoriteLabel,
                favoritedLabel = state.favoritedLabel,
                shuffleEnabled = state.shuffleEnabled,
                shuffleLabel = state.shuffleLabel,
                repeatLabel = state.repeatLabel,
                repeatOffLabel = state.repeatOffLabel,
                queueLabel = state.queueLabel
            ),
            onFavorite = onFavorite,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            onQueue = onQueue
        )
    }
}

@Composable
private fun ArtworkSection(
    slice: ArtworkSlice,
    title: String,
    subtitle: String,
    showLyricsLabel: String,
    artworkMax: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onToggle: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val uri = remember(slice.artUriString) { slice.artUriString?.let { android.net.Uri.parse(it) } }
    val artworkShape = RoundedCornerShape(28.dp)
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
                .shadow(
                    elevation = if (compact) 16.dp else 26.dp,
                    shape = artworkShape,
                    clip = false,
                    ambientColor = p.shadow.copy(alpha = 0.5f),
                    spotColor = p.accent.copy(alpha = 0.45f)
                )
                .clip(artworkShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onToggle
                )
                .echoPressScale(interaction)
                .semantics { contentDescription = showLyricsLabel },
            cornerRadius = 28.dp,
            fallbackTextSize = if (compact) 46.sp else 54.sp,
            targetSize = 512.dp,
            backgroundColor = p.surfaceVariant,
            fallbackResId = R.drawable.ic_echo_launcher
        )
    }
}

@Composable
private fun LyricsStage(
    slice: LyricsSlice,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val p = EchoTheme.colors()
    val activeIndex = slice.lines.indexOfFirst { it.active }
    val listState = rememberLazyListState()
    val interaction = remember { MutableInteractionSource() }
    var viewportHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(activeIndex, slice.lines.size, viewportHeightPx) {
        if (activeIndex >= 0 && activeIndex < slice.lines.size) {
            listState.animateScrollToItem(
                activeIndex,
                scrollOffset = centeredLyricScrollOffset(viewportHeightPx)
            )
        }
    }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle
            )
            .echoPressScale(interaction)
            .semantics { contentDescription = slice.showArtworkLabel }
    ) {
        if (slice.lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EchoIcon(EchoIconKind.Lyrics, Modifier.size(28.dp), p.muted)
                    Text(
                        slice.status.ifBlank { slice.noLyricsFoundLabel },
                        style = EchoTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.heading.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        slice.title,
                        style = EchoTypography.caption,
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { viewportHeightPx = it.height },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 52.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = slice.lines,
                    key = { index, line -> "$index:${line.text.hashCode()}" }
                ) { _, line ->
                    OverlayLyricRow(line)
                }
            }
        }
    }
}

private fun centeredLyricScrollOffset(viewportHeightPx: Int): Int {
    if (viewportHeightPx <= 0) {
        return 0
    }
    return -(viewportHeightPx / 2 - ACTIVE_LYRIC_ESTIMATED_HEIGHT_PX / 2).coerceAtLeast(0)
}

private const val ACTIVE_LYRIC_ESTIMATED_HEIGHT_PX = 64

@Composable
private fun OverlayLyricRow(line: LyricUiLine) {
    val p = EchoTheme.colors()
    val color by animateColorAsState(
        targetValue = if (line.active) p.heading else p.muted.copy(alpha = 0.68f),
        animationSpec = tween(durationMillis = EchoMotion.CROSSFADE_MS),
        label = "lyricColor"
    )
    val alpha by animateFloatAsState(
        targetValue = if (line.active) 1f else 0.72f,
        animationSpec = EchoMotion.floatSpring(),
        label = "lyricAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (line.active) 1.045f else 1f,
        animationSpec = EchoMotion.floatSpring(),
        label = "lyricScale"
    )
    val lift by animateFloatAsState(
        targetValue = if (line.active) -5f else 0f,
        animationSpec = EchoMotion.floatSpring(),
        label = "lyricLift"
    )
    Text(
        text = line.text,
        style = if (line.active) {
            EchoTypography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 29.sp)
        } else {
            EchoTypography.body.copy(fontSize = 18.sp, lineHeight = 25.sp)
        },
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = lift
            }
            .padding(horizontal = 12.dp, vertical = if (line.active) 7.dp else 5.dp),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TrackInfoSection(
    slice: TrackInfoSlice,
    compact: Boolean,
    centered: Boolean = false,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val titleSize = if (compact) 27.sp else 30.sp
    val titleLineHeight = if (compact) 33.sp else 36.sp
    val subtitleSize = if (compact) 16.sp else 18.sp
    val subtitleLineHeight = if (compact) 22.sp else 24.sp
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            slice.title,
            style = EchoTypography.display.copy(fontSize = titleSize, lineHeight = titleLineHeight),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = if (centered) Modifier.fillMaxWidth() else Modifier
        )
        if (slice.subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                slice.subtitle,
                style = EchoTypography.body.copy(fontSize = subtitleSize, lineHeight = subtitleLineHeight),
                color = p.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = if (centered) Modifier.fillMaxWidth() else Modifier
            )
        }
    }
}

@Composable
private fun PlaybackErrorBanner(
    slice: PlaybackErrorSlice,
    compact: Boolean,
    onRetry: () -> Unit
) {
    AnimatedVisibility(
        visible = slice.visible,
        enter = fadeIn(animationSpec = tween(durationMillis = EchoMotion.FAST_CROSSFADE_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = EchoMotion.FAST_CROSSFADE_MS))
    ) {
        val p = EchoTheme.colors()
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 10.dp else 14.dp)
                .echoGlassLayer(p, EchoShapes.medium)
                .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(EchoIconKind.Refresh, Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    slice.title,
                    style = EchoTypography.label,
                    color = p.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    slice.message,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = onRetry,
                interactionSource = interaction,
                modifier = Modifier
                    .echoPressScale(interaction)
                    .semantics { contentDescription = slice.retryLabel },
                shape = EchoShapes.full,
                color = p.accentSoft.copy(alpha = 0.72f)
            ) {
                Text(
                    slice.retryLabel,
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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
        progressLabel = slice.playbackProgressLabel,
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
        LargeTransportButton(EchoIconKind.Previous, slice.previousLabel) { onPrevious.run() }
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = { onPlayPause.run() },
            interactionSource = interaction,
            modifier = Modifier
                .size(playButtonSize)
                .echoPressScale(interaction)
                .semantics { contentDescription = if (slice.playing) slice.pauseLabel else slice.playLabel },
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
        LargeTransportButton(EchoIconKind.Next, slice.nextLabel) { onNext.run() }
    }
}

@Composable
private fun ModeControls(
    slice: ModeSlice,
    onFavorite: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onQueue: Runnable
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            EchoIconKind.Heart,
            if (slice.favorite) slice.favoritedLabel else slice.favoriteLabel,
            active = slice.favorite
        ) { onFavorite.run() }
        CircleIconButton(EchoIconKind.Shuffle, slice.shuffleLabel, active = slice.shuffleEnabled) { onShuffle.run() }
        CircleIconButton(
            EchoIconKind.Repeat,
            slice.repeatLabel,
            active = slice.repeatLabel != slice.repeatOffLabel
        ) { onRepeat.run() }
        CircleIconButton(EchoIconKind.Queue, slice.queueLabel) { onQueue.run() }
    }
}

@Composable
private fun NowPlayingMoreMenu(
    slice: MoreMenuSlice,
    toggleIcon: EchoIconKind,
    onFavorite: Runnable,
    onAddToPlaylist: Runnable,
    onQueue: Runnable,
    onToggleView: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val p = EchoTheme.colors()
    Box {
        CircleIconButton(EchoIconKind.More, slice.moreLabel) {
            expanded = true
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (slice.favorite) slice.favoritedLabel else slice.favoriteLabel) },
                leadingIcon = {
                    EchoIcon(EchoIconKind.Heart, Modifier.size(18.dp), if (slice.favorite) p.accent else p.muted)
                },
                onClick = {
                    expanded = false
                    onFavorite.run()
                }
            )
            DropdownMenuItem(
                text = { Text(slice.addToPlaylistLabel) },
                leadingIcon = { EchoIcon(EchoIconKind.PlaylistAdd, Modifier.size(18.dp), p.muted) },
                onClick = {
                    expanded = false
                    onAddToPlaylist.run()
                }
            )
            DropdownMenuItem(
                text = { Text(slice.queueLabel) },
                leadingIcon = { EchoIcon(EchoIconKind.Queue, Modifier.size(18.dp), p.muted) },
                onClick = {
                    expanded = false
                    onQueue.run()
                }
            )
            DropdownMenuItem(
                text = { Text(slice.toggleViewLabel) },
                leadingIcon = { EchoIcon(toggleIcon, Modifier.size(18.dp), p.muted) },
                onClick = {
                    expanded = false
                    onToggleView()
                }
            )
        }
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
            .size(60.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = desc },
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(34.dp), p.text)
        }
    }
}

@Composable
private fun ScrubProgress(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: SeekAction,
    progressLabel: String,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
    // Locally-advancing position so the bar glides between the ~1s authoritative pushes. The draw
    // lambda reads this State, so the smooth motion only redraws the Canvas — it never recomposes.
    Canvas(
        modifier = modifier
            .semantics { contentDescription = progressLabel }
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
        val trackHeight = 6.dp.toPx()
        val centerY = size.height / 2f
        val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        drawRoundRect(
            color = p.surfaceVariant.copy(alpha = 0.55f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = p.accent,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * progress, trackHeight),
            cornerRadius = radius
        )
        val thumbRadius = 8.dp.toPx()
        val thumbX = (size.width * progress).coerceIn(thumbRadius, size.width - thumbRadius)
        drawCircle(
            color = p.onAccent.copy(alpha = 0.9f),
            radius = thumbRadius + 2.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = p.accent,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}
