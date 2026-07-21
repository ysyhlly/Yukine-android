package app.yukine.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R
import app.yukine.TrackDownloadItem
import kotlinx.coroutines.delay
import android.os.SystemClock
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
    val songInfo: String = "",
    val sourceInfo: String = "",
    val sourceOptions: List<NowPlayingSourceOption> = emptyList(),
    val activeDownload: TrackDownloadItem? = null,
    val playbackQuality: String = "",
    val audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    val appVolume: Float = 1.0f,
    val trackId: Long = -1L,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val primaryVisible: Boolean = true,
    val translationVisible: Boolean = true,
    val romanizationVisible: Boolean = false,
    val onShare: Runnable = Runnable {},
    val onDownload: Runnable = Runnable {},
    val onImportLyrics: Runnable = Runnable {},
    val onClearLyrics: Runnable = Runnable {},
    val onPrimaryVisibleChange: (Boolean) -> Unit = {},
    val onTranslationVisibleChange: (Boolean) -> Unit = {},
    val onRomanizationVisibleChange: (Boolean) -> Unit = {},
    val lyricsOffsetMs: Long = 0L
)

data class NowPlayingSourceOption(
    val label: String,
    val description: String = "",
    val selected: Boolean = false,
    val available: Boolean = true,
    val onClick: Runnable = Runnable {}
)

data class NowPlayingGestureActions(
    val onPrevious: Runnable,
    val onNext: Runnable,
    val onClose: Runnable
) {
    companion object {
        val Empty = NowPlayingGestureActions(
            Runnable {},
            Runnable {},
            Runnable {}
        )
    }
}

@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    immersive: Boolean = false,
    onImmersiveChanged: (Boolean) -> Unit = {},
    gestureActions: NowPlayingGestureActions = NowPlayingGestureActions.Empty,
    onLyricSeek: (Long) -> Unit = {}
) {
    val seekFromLyric: (Long) -> Unit = { lyricPositionMs ->
        onLyricSeek(playbackPositionForLyricMs(lyricPositionMs, state.lyricsOffsetMs))
    }

    AnimatedContent(
        targetState = immersive,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "nowPlayingMode"
    ) { isImmersive ->
        if (isImmersive) {
            ImmersiveLyricsView(
                lyrics = state.lyrics,
                playbackPositionMs = state.positionMs,
                playing = state.playing,
                trackId = state.trackId,
                offsetMs = state.lyricsOffsetMs,
                title = state.title,
                subtitle = state.subtitle,
                albumArtUri = state.albumArtUri,
                onLyricSeek = seekFromLyric,
                onExit = { onImmersiveChanged(false) }
            )
        } else {
            NowPlayingNormalView(
                state = state,
                onArtworkClick = {
                    if (state.lyrics.isNotEmpty()) {
                        onImmersiveChanged(true)
                    }
                },
                gestureActions = gestureActions,
                onLyricSeek = seekFromLyric
            )
        }
    }
}

@Composable
private fun ImmersiveLyricsView(
    lyrics: List<LyricUiLine>,
    playbackPositionMs: Long,
    playing: Boolean,
    trackId: Long,
    offsetMs: Long,
    title: String,
    subtitle: String,
    albumArtUri: Uri?,
    onLyricSeek: (Long) -> Unit,
    onExit: () -> Unit
) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val positionMs = rememberSmoothLyricPosition(
        trackId = trackId,
        playbackPositionMs = playbackPositionMs,
        playing = playing,
        offsetMs = offsetMs
    )
    val activeIndex = lyrics.indexOfFirst { line -> line.isActiveAt(positionMs) }
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
                    ImmersiveLyricRow(
                        line = line.copy(active = line.isActiveAt(positionMs)),
                        positionMs = positionMs,
                        onSeek = onLyricSeek
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ImmersiveLyricRow(line: LyricUiLine, positionMs: Long, onSeek: (Long) -> Unit) {
    val p = EchoTheme.colors()
    val copyText = rememberCopyTextAction()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (line.active) 12.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = EchoShapes.small,
            color = Color.Transparent,
            modifier = Modifier
                .combinedClickable(
                    onClick = { onSeek(line.timeMs) },
                    onLongClick = { copyText(line.text) }
                )
        ) {
            LayeredLyricText(
                line = line,
                positionMs = positionMs,
                primaryStyle = if (line.active) {
                    EchoTypography.headline.copy(fontSize = 24.sp, lineHeight = 32.sp)
                } else {
                    EchoTypography.body.copy(fontSize = 16.sp, lineHeight = 24.sp)
                },
                primaryColor = if (line.active) p.accent else p.muted.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun NowPlayingNormalView(
    state: NowPlayingUiState,
    onArtworkClick: () -> Unit,
    gestureActions: NowPlayingGestureActions,
    onLyricSeek: (Long) -> Unit
) {
    val p = EchoTheme.colors()
    val gestureModifier = if (gestureActions == NowPlayingGestureActions.Empty) {
        Modifier
    } else {
        Modifier.nowPlayingGestureInput(
            actions = gestureActions
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.sectionSpacing)
    ) {
        item(key = "page-title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(gestureModifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EchoPageTitle(
                    state.pageTitle.ifBlank { "Now" },
                    modifier = Modifier.weight(1f)
                )
                YukineDownloadOrb(
                    item = state.activeDownload,
                    playbackQuality = state.playbackQuality,
                    audioMotion = state.audioMotion,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        item(key = "deck") {
            EchoGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = EchoShapes.large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArtHero(
                        state.albumArtUri, state.title, state.subtitle,
                        onClick = onArtworkClick,
                        modifier = Modifier.then(gestureModifier)
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
                    NowPlayingQuickActions(state.onShare, state.onDownload)
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

        if (state.songInfo.isNotBlank() || state.sourceInfo.isNotBlank() || state.sourceOptions.isNotEmpty()) {
            item(key = "song-info") {
                SongInfoPanel(state.songInfo, state.sourceInfo, state.sourceOptions)
            }
        }

        item(key = "lyrics-panel") {
            LyricsPanel(
                title = state.lyricsTitle,
                status = state.lyricsStatus,
                lines = state.lyrics,
                playbackPositionMs = state.positionMs,
                playing = state.playing,
                trackId = state.trackId,
                offsetMs = state.lyricsOffsetMs,
                primaryVisible = state.primaryVisible,
                translationVisible = state.translationVisible,
                romanizationVisible = state.romanizationVisible,
                onImportLyrics = state.onImportLyrics,
                onClearLyrics = state.onClearLyrics,
                onPrimaryVisibleChange = state.onPrimaryVisibleChange,
                onTranslationVisibleChange = state.onTranslationVisibleChange,
                onRomanizationVisibleChange = state.onRomanizationVisibleChange,
                onLyricSeek = onLyricSeek
            )
        }
    }
}

@Composable
private fun SongInfoPanel(
    songInfo: String,
    sourceInfo: String,
    sourceOptions: List<NowPlayingSourceOption>
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (songInfo.isNotBlank()) {
                Text(
                    "歌曲介绍",
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading
                )
                Text(
                    songInfo,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sourceInfo.isNotBlank()) {
                Text(
                    sourceInfo,
                    style = EchoTypography.small,
                    color = p.accent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sourceOptions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "音源切换",
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading
                )
                sourceOptions.forEach { option ->
                    SourceOptionRow(option)
                }
            }
        }
    }
}

@Composable
private fun SourceOptionRow(option: NowPlayingSourceOption) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { if (option.available) option.onClick.run() },
        enabled = option.available,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = EchoShapes.small,
        color = when {
            option.selected -> p.accentSoft.copy(alpha = 0.68f)
            option.available -> p.surfaceVariant.copy(alpha = 0.26f)
            else -> p.surfaceVariant.copy(alpha = 0.12f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(
                if (option.selected) EchoIconKind.Check else EchoIconKind.Network,
                Modifier.size(16.dp),
                if (option.selected) p.accent else p.muted
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (option.available) p.text else p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (option.description.isNotBlank()) {
                    Text(
                        option.description,
                        style = EchoTypography.small,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingQuickActions(onShare: Runnable, onDownload: Runnable) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NowPlayingQuickAction(
            label = "分享音源卡片",
            icon = EchoIconKind.Network,
            modifier = Modifier.weight(1f),
            onClick = { onShare.run() }
        )
        NowPlayingQuickAction(
            label = "下载",
            icon = EchoIconKind.Import,
            modifier = Modifier.weight(1f),
            onClick = { onDownload.run() }
        )
    }
}

@Composable
private fun NowPlayingQuickAction(
    label: String,
    icon: EchoIconKind,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .height(42.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = p.accentSoft.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(18.dp), p.accent)
            Text(
                label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Modifier.nowPlayingGestureInput(
    actions: NowPlayingGestureActions
): Modifier {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val dominantRatio = 1.25f
    var totalX by remember { mutableStateOf(0f) }
    var totalY by remember { mutableStateOf(0f) }
    return pointerInput(actions) {
        detectDragGestures(
            onDragStart = {
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
                        if (totalY > 0f) {
                            actions.onClose.run()
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
private fun AlbumArtHero(
    uri: Uri?,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    Box(
        modifier = modifier
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
        modifier = modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
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
private fun LyricsPanel(
    title: String,
    status: String,
    lines: List<LyricUiLine>,
    playbackPositionMs: Long,
    playing: Boolean,
    trackId: Long,
    offsetMs: Long,
    primaryVisible: Boolean,
    translationVisible: Boolean,
    romanizationVisible: Boolean,
    onImportLyrics: Runnable,
    onClearLyrics: Runnable,
    onPrimaryVisibleChange: (Boolean) -> Unit,
    onTranslationVisibleChange: (Boolean) -> Unit,
    onRomanizationVisibleChange: (Boolean) -> Unit,
    onLyricSeek: (Long) -> Unit
) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val positionMs = rememberSmoothLyricPosition(
        trackId = trackId,
        playbackPositionMs = playbackPositionMs,
        playing = playing,
        offsetMs = offsetMs
    )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    style = EchoTypography.title,
                    color = p.heading,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .weight(1f)
                )
                LyricsAction("导入/替换", EchoIconKind.Import) { onImportLyrics.run() }
                if (lines.isNotEmpty()) {
                    LyricsAction("清除", EchoIconKind.Remove) { onClearLyrics.run() }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricsTrackToggle(
                    "原文",
                    primaryVisible,
                    Modifier.weight(1f)
                ) { onPrimaryVisibleChange(!primaryVisible) }
                LyricsTrackToggle(
                    "翻译",
                    translationVisible,
                    Modifier.weight(1f)
                ) { onTranslationVisibleChange(!translationVisible) }
                LyricsTrackToggle(
                    "罗马音",
                    romanizationVisible,
                    Modifier.weight(1f)
                ) { onRomanizationVisibleChange(!romanizationVisible) }
            }
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
                        LyricRow(
                            line = line.copy(active = line.isActiveAt(positionMs)),
                            positionMs = positionMs,
                            onSeek = onLyricSeek
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSmoothLyricPosition(
    trackId: Long,
    playbackPositionMs: Long,
    playing: Boolean,
    offsetMs: Long
): Long {
    var smoothPositionMs by remember(trackId) { mutableStateOf(playbackPositionMs) }
    LaunchedEffect(trackId, playbackPositionMs, playing) {
        smoothPositionMs = playbackPositionMs
        if (playing) {
            val anchorRealtime = SystemClock.elapsedRealtime()
            while (true) {
                delay(33L)
                smoothPositionMs = playbackPositionMs +
                    (SystemClock.elapsedRealtime() - anchorRealtime)
            }
        }
    }
    return adjustedLyricPositionMs(smoothPositionMs, offsetMs)
}

@Composable
private fun LyricRow(line: LyricUiLine, positionMs: Long, onSeek: (Long) -> Unit) {
    val p = EchoTheme.colors()
    val copyText = rememberCopyTextAction()
    Surface(
        shape = EchoShapes.small,
        color = if (line.active) p.accentSoft else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSeek(line.timeMs) },
                onLongClick = { copyText(line.text) }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (line.active) 10.dp else 8.dp)
        ) {
            LayeredLyricText(
                line = line,
                positionMs = positionMs,
                primaryStyle = if (line.active) {
                    EchoTypography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    )
                } else {
                    EchoTypography.body
                },
                primaryColor = if (line.active) p.accent else p.muted
            )
        }
    }
}

@Composable
private fun LayeredLyricText(
    line: LyricUiLine,
    positionMs: Long,
    primaryStyle: TextStyle,
    primaryColor: Color
) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = highlightedWords(line, positionMs, primaryColor, p.muted),
            style = primaryStyle,
            color = primaryColor,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (line.translation.isNotBlank()) {
            Text(
                line.translation,
                style = primaryStyle,
                color = primaryColor,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (line.romanization.isNotBlank()) {
            Text(
                line.romanization,
                style = EchoTypography.small,
                color = p.muted.copy(alpha = if (line.active) 0.7f else 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun highlightedWords(
    line: LyricUiLine,
    positionMs: Long,
    activeColor: Color,
    inactiveColor: Color
): AnnotatedString {
    if (!line.active || line.words.isEmpty()) return AnnotatedString(line.text)
    val builder = AnnotatedString.Builder(line.text)
    var searchFrom = 0
    line.words.forEach { word ->
        val (start, end) = word.textBounds(line.text, searchFrom) ?: return@forEach
        val completed = positionMs >= word.endMs
        val current = word.isActiveAt(positionMs)
        when {
            completed -> {
                builder.addStyle(SpanStyle(color = activeColor), start, end)
            }
            current -> {
                val fraction = word.progressAt(positionMs)
                val charCount = end - start
                val filledChars = (fraction * charCount).roundToInt().coerceIn(0, charCount)
                val splitAt = start + filledChars
                if (splitAt > start) {
                    builder.addStyle(
                        SpanStyle(color = activeColor, fontWeight = FontWeight.Bold),
                        start, splitAt
                    )
                }
                if (splitAt < end) {
                    builder.addStyle(SpanStyle(color = inactiveColor), splitAt, end)
                }
            }
            else -> {
                builder.addStyle(SpanStyle(color = inactiveColor), start, end)
            }
        }
        searchFrom = end
    }
    return builder.toAnnotatedString()
}

@Composable
private fun LyricsAction(label: String, icon: EchoIconKind, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        shape = EchoShapes.small,
        color = p.accentSoft.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(14.dp), p.accent)
            Text(label, style = EchoTypography.small, color = p.accent)
        }
    }
}

@Composable
private fun LyricsTrackToggle(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = EchoShapes.small,
        color = if (enabled) p.accentSoft.copy(alpha = 0.55f) else p.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = EchoTypography.small,
            color = if (enabled) p.accent else p.muted,
            textAlign = TextAlign.Center
        )
    }
}
