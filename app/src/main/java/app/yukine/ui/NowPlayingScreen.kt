package app.yukine.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.R
import kotlin.math.abs
import kotlin.math.roundToInt

data class NowPlayingUiState(
    val pageTitle: String,
    val title: String,
    val subtitle: String,
    val queueMetricLabel: String,
    val queueLabel: String,
    val durationMetricLabel: String,
    val durationLabel: String,
    val statusLabel: String,
    val albumArtUri: Uri?,
    val lyricsTitle: String,
    val lyricsStatus: String,
    val lyrics: List<LyricUiLine>,
    val artistName: String = "",
    val albumName: String = "",
    val audioSpec: String = "",
    val appVolume: Float = 1.0f
)

data class LyricUiLine(val text: String, val active: Boolean)

data class NowPlayingGestureActions(
    val onPrevious: Runnable,
    val onNext: Runnable,
    val onVolumeChange: (Float) -> Unit
) {
    companion object {
        val Empty = NowPlayingGestureActions(
            Runnable {},
            Runnable {},
            {}
        )
    }
}

@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    defaultImmersive: Boolean = false,
    onDefaultImmersiveConsumed: () -> Unit = {},
    gestureActions: NowPlayingGestureActions = NowPlayingGestureActions.Empty
) {
    var immersiveLyrics by remember { mutableStateOf(false) }
    val activeLyricIndex = state.lyrics.indexOfFirst { it.active }

    LaunchedEffect(defaultImmersive, state.title, state.subtitle) {
        if (defaultImmersive) {
            immersiveLyrics = true
            onDefaultImmersiveConsumed()
        }
    }

    AnimatedContent(
        targetState = immersiveLyrics,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "nowPlayingMode"
    ) { isImmersive ->
        if (isImmersive) {
            ImmersiveLyricsView(
                lyrics = state.lyrics,
                activeIndex = activeLyricIndex,
                title = state.title,
                subtitle = state.subtitle,
                albumArtUri = state.albumArtUri,
                onExit = { immersiveLyrics = false }
            )
        } else {
            NowPlayingNormalView(
                state = state,
                activeLyricIndex = activeLyricIndex,
                onArtworkClick = { if (state.lyrics.isNotEmpty()) immersiveLyrics = true },
                gestureActions = gestureActions
            )
        }
    }
}

@Composable
private fun ImmersiveLyricsView(
    lyrics: List<LyricUiLine>,
    activeIndex: Int,
    title: String,
    subtitle: String,
    albumArtUri: Uri?,
    onExit: () -> Unit
) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.scrollToItem(activeIndex, scrollOffset = -200)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (albumArtUri != null) {
            AsyncArtwork(
                uri = albumArtUri,
                title = title,
                subtitle = subtitle,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.35f },
                cornerRadius = 0.dp,
                fallbackTextSize = 0.sp,
                targetSize = 256.dp,
                backgroundColor = p.background,
                fallbackResId = R.drawable.ic_echo_launcher
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(p.background.copy(alpha = 0.65f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onExit() }
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = EchoTypography.title,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(32.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { index, line -> "imm-$index:${line.text.hashCode()}" }
                ) { _, line ->
                    ImmersiveLyricRow(line)
                }
            }
        }
        }
    }
}

@Composable
private fun ImmersiveLyricRow(line: LyricUiLine) {
    val p = EchoTheme.colors()
    val copyText = rememberCopyTextAction()
    Surface(
        onClick = { copyText(line.text) },
        shape = EchoShapes.small,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = line.text,
            style = if (line.active) {
                EchoTypography.headline.copy(fontSize = 24.sp, lineHeight = 32.sp)
            } else {
                EchoTypography.body.copy(fontSize = 16.sp, lineHeight = 24.sp)
            },
            color = if (line.active) p.accent else p.muted.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (line.active) 12.dp else 6.dp),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NowPlayingNormalView(
    state: NowPlayingUiState,
    activeLyricIndex: Int,
    onArtworkClick: () -> Unit,
    gestureActions: NowPlayingGestureActions
) {
    val p = EchoTheme.colors()
    val gestureModifier = if (gestureActions == NowPlayingGestureActions.Empty) {
        Modifier
    } else {
        Modifier.nowPlayingGestureInput(
            currentVolume = state.appVolume,
            actions = gestureActions
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.sectionSpacing)
    ) {
        item(key = "page-title") {
            EchoPageTitle(state.pageTitle.ifBlank { "Now" })
        }
        item(key = "deck") {
            EchoGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(gestureModifier),
                shape = EchoShapes.large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArtHero(
                        state.albumArtUri, state.title, state.subtitle,
                        onClick = onArtworkClick
                    )
                    Spacer(Modifier.height(14.dp))
                    CopyableMetadataText(
                        text = state.title,
                        style = EchoTypography.headline,
                        color = p.heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (state.artistName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        CopyableMetadataText(
                            text = state.artistName,
                            style = EchoTypography.body,
                            color = p.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (state.albumName.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        CopyableMetadataText(
                            text = state.albumName,
                            style = EchoTypography.caption,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (state.audioSpec.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            state.audioSpec,
                            style = EchoTypography.small,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(state.queueMetricLabel, state.queueLabel, Modifier.weight(1f))
                        MetricCard(state.durationMetricLabel, state.durationLabel, Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.statusLabel.isNotBlank()) {
            item(key = "status") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoGlassLayer(p, EchoShapes.medium),
                    shape = EchoShapes.medium,
                    color = Color.Transparent
                ) {
                    Text(
                        state.statusLabel,
                        style = EchoTypography.caption,
                        color = p.muted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        item(key = "lyrics-panel") {
            LyricsPanel(
                title = state.lyricsTitle,
                status = state.lyricsStatus,
                lines = state.lyrics,
                activeIndex = activeLyricIndex
            )
        }
    }
}

@Composable
private fun Modifier.nowPlayingGestureInput(
    currentVolume: Float,
    actions: NowPlayingGestureActions
): Modifier {
    val context = LocalContext.current
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val dominantRatio = 1.25f
    val volumeStepDistance = with(density) { 42.dp.toPx() }
    var startVolume by remember { mutableStateOf(currentVolume.coerceIn(0f, 1f)) }
    var totalX by remember { mutableStateOf(0f) }
    var totalY by remember { mutableStateOf(0f) }
    return pointerInput(currentVolume, actions) {
        detectDragGestures(
            onDragStart = {
                startVolume = currentVolume.coerceIn(0f, 1f)
                totalX = 0f
                totalY = 0f
            },
            onDrag = { change, dragAmount ->
                totalX += dragAmount.x
                totalY += dragAmount.y
                if (abs(totalX) > swipeThreshold || abs(totalY) > swipeThreshold) {
                    change.consume()
                }
            },
            onDragEnd = {
                val absX = abs(totalX)
                val absY = abs(totalY)
                when {
                    absX >= swipeThreshold && absX > absY * dominantRatio -> {
                        if (totalX < 0f) {
                            actions.onNext.run()
                        } else {
                            actions.onPrevious.run()
                        }
                    }
                    absY >= swipeThreshold && absY > absX * dominantRatio -> {
                        val deltaSteps = (-totalY / volumeStepDistance).roundToInt()
                        if (deltaSteps != 0) {
                            val nextVolume = (startVolume + deltaSteps * 0.05f).coerceIn(0f, 1f)
                            actions.onVolumeChange(nextVolume)
                            Toast.makeText(
                                context,
                                "音量 ${(nextVolume * 100).roundToInt()}%",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun CopyableMetadataText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    overflow: TextOverflow,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val copyText = rememberCopyTextAction()
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier.clickable(enabled = text.isNotBlank()) {
            copyText(text)
        }
    )
}

@Composable
private fun rememberCopyTextAction(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return { value ->
        if (value.isNotBlank()) {
            clipboard.setText(AnnotatedString(value))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun AlbumArtHero(uri: Uri?, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .size(EchoMobileLayoutMetrics.nowPlayingArtworkSize)
            .clip(EchoShapes.large)
            .then(
                if (onClick != null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (uri == null) {
            EchoArtworkFallback(title, subtitle, Modifier.fillMaxSize(), 12.dp, 56.sp)
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = EchoShapes.large,
                shadowElevation = 10.dp,
                color = p.surfaceVariant
            ) {
                AsyncArtwork(
                    uri = uri,
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = EchoMobileLayoutMetrics.nowPlayingArtworkCornerRadius,
                    fallbackTextSize = 56.sp,
                    targetSize = 512.dp,
                    backgroundColor = p.surfaceVariant,
                    fallbackResId = R.drawable.ic_echo_launcher
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = EchoTypography.caption, color = p.muted, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = EchoTypography.title,
                color = p.text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsPanel(title: String, status: String, lines: List<LyricUiLine>, activeIndex: Int) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    EchoGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = EchoMobileLayoutMetrics.lyricsPanelMinHeight,
                max = EchoMobileLayoutMetrics.lyricsPanelMaxHeight
            ),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = EchoTypography.title,
                color = p.heading,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            if (lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        status,
                        style = EchoTypography.body,
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EchoMobileLayoutMetrics.lyricsListHeight),
                    contentPadding = PaddingValues(vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "$index:${line.text.hashCode()}" }
                    ) { _, line ->
                        LyricRow(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricUiLine) {
    val p = EchoTheme.colors()
    val copyText = rememberCopyTextAction()
    Surface(
        onClick = { copyText(line.text) },
        shape = EchoShapes.small,
        color = if (line.active) p.accentSoft else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = line.text,
            style = if (line.active) {
                EchoTypography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
            } else {
                EchoTypography.body
            },
            color = if (line.active) p.accent else p.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (line.active) 10.dp else 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
