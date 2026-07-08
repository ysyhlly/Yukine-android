package app.yukine.ui

import android.net.Uri
import app.yukine.TrackDownloadItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.feature.uicommon.R

/** Number of natural-week columns shown in the recap heatmap (desktop parity). */
internal const val HEATMAP_WEEK_COLUMNS = 12

data class HomeDashboardUiState(
    val title: String = "",
    val subtitle: String = "",
    val heroTitle: String = "今天想听点什么？",
    val heroSubtitle: String = "",
    val continueTitle: String = "",
    val continueSubtitle: String = "",
    val continueDetail: String = "",
    val continueAlbumArtUri: Uri? = null,
    val continueProgress: Float = 0f,
    val continuePlaying: Boolean = false,
    val stats: List<HomeDashboardStatUiState> = emptyList(),
    val recent: List<HomeDashboardRecentUiState> = emptyList(),
    val recentTabIndex: Int = 0,
    val weeklyTitle: String = "",
    val weeklyPlays: Int = 0,
    val weeklyDuration: String = "",
    val weeklyBars: List<Float> = List(7) { 0.08f },
    val heatmap: List<HomeDashboardHeatmapDay> = emptyList(),
    val heatmapMonths: List<HomeDashboardHeatmapMonth> = emptyList(),
    val activeWeeks: Int = 0,
    val activeDays: Int = 0,
    val empty: Boolean = false,
    val streamingConnected: Boolean = false
)

data class HomeDashboardHeatmapDay(
    val date: String,
    val count: Int,
    val dayOfWeek: Int,
    val future: Boolean = false
)

/** A month label spanning one or more heatmap week-columns. */
data class HomeDashboardHeatmapMonth(
    val label: String,
    // Zero-based index of the first week column this label covers.
    val weekIndex: Int,
    // Number of consecutive week columns the label spans.
    val span: Int
)

data class HomeDashboardStatUiState(
    val label: String,
    val value: String,
    val mode: String
)

data class HomeDashboardRecentUiState(
    val id: Long,
    val title: String,
    val subtitle: String,
    val detail: String,
    val albumArtUri: Uri?
)

data class HomeDashboardActions(
    val onOpenStat: List<Runnable>,
    val onContinue: Runnable,
    val onOpenNowPlaying: Runnable,
    val onPlayRecent: List<Runnable>,
    val onRefresh: Runnable,
    val onViewQueue: Runnable,
    val onShuffleAll: Runnable,
    val onRecentTabChanged: (Int) -> Unit,
    val onDailyRecommend: Runnable = Runnable { },
    val onHeartbeatRecommend: Runnable = Runnable { },
    val onOpenCollections: Runnable = Runnable { },
    val onConnectStreaming: Runnable = Runnable { },
    val onSearch: Runnable = Runnable { }
)

@Composable
fun HomeDashboardScreen(
    state: HomeDashboardUiState,
    actions: HomeDashboardActions,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val p = EchoTheme.colors()
    CollapsibleSearchHeader(
        header = { HomeSearchCard(actions, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero section with Now Playing card
            item("hero") {
                Box(Modifier.echoEnter(0)) { HeroSection(state, actions) }
            }
            // Streaming guide (only when not connected)
            if (!state.streamingConnected) {
                item("streaming-guide") {
                    Box(Modifier.echoEnter(1)) { StreamingGuideCard { actions.onConnectStreaming.run() } }
                }
            }
            // Recommendations (每日推荐 / 心动推荐) kept near the top so they're visible on first screen
            item("recommendations") {
                Box(Modifier.echoEnter(if (state.streamingConnected) 1 else 2)) {
                    RecommendationCards(actions, enabled = state.streamingConnected)
                }
            }
            // Stats grid (2x2 for mobile)
            item("stats") {
                Box(Modifier.echoEnter(2)) { StatGrid(state.stats, actions) }
            }
            // Recent activity (horizontal scroll)
            item("recent") {
                Box(Modifier.echoEnter(3)) { RecentActivitySection(state, actions) }
            }
            // Weekly recap with heatmap
            item("weekly") {
                Box(Modifier.echoEnter(4)) { WeeklyRecapSection(state) }
            }
        }
    }
}

// Hero section (mobile optimized)

@Composable
private fun HomeSearchCard(
    actions: HomeDashboardActions,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion
) {
    YukineSearchBar(
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    ) { actions.onSearch.run() }
}

@Composable
private fun HeroSection(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Now Playing Card (full width on mobile)
        NowPlayingCard(state, actions)
        // Hero text and actions
        HeroPanel(state, actions)
    }
}

@Composable
private fun HeroPanel(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(p.accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "今日回声",
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent
            )
        }
        // Title
        Text(
            state.heroTitle,
            style = EchoTypography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Subtitle
        Text(
            state.heroSubtitle.ifBlank { "继续最近播放，或从最近入库里挑一张封面开始。" },
            style = EchoTypography.body,
            color = p.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Action buttons
        HeroActionButtons(actions)
    }
}

@Composable
private fun HeroActionButtons(actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    // Horizontal scrollable action buttons for mobile
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        item {
            // Primary action - Continue playback
            Surface(
                onClick = { actions.onContinue.run() },
                modifier = Modifier
                    .height(40.dp)
                    .semantics { contentDescription = "继续播放" },
                shape = EchoShapes.medium,
                color = p.accent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoIcon(EchoIconKind.Play, Modifier.size(18.dp), p.onAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("继续播放", style = EchoTypography.label, color = p.onAccent)
                }
            }
        }
        item { HeroSecondaryButton("队列", EchoIconKind.Queue) { actions.onViewQueue.run() } }
        item { HeroSecondaryButton("随机", EchoIconKind.Shuffle) { actions.onShuffleAll.run() } }
        item { HeroSecondaryButton("收藏", EchoIconKind.Heart) { actions.onOpenCollections.run() } }
        item { HeroSecondaryButton("搜索", EchoIconKind.Search) { actions.onSearch.run() } }
        item { HeroSecondaryButton("刷新", EchoIconKind.Sync) { actions.onRefresh.run() } }
    }
}

@Composable
private fun HeroSecondaryButton(label: String, icon: EchoIconKind, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = p.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(16.dp), p.text)
            Spacer(Modifier.width(6.dp))
            Text(label, style = EchoTypography.label, color = p.text)
        }
    }
}

// Now playing card (full width for mobile)

@Composable
private fun NowPlayingCard(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { actions.onOpenNowPlaying.run() },
        interactionSource = interaction,
        modifier = Modifier
            .echoPressScale(interaction)
            .fillMaxWidth()
            .semantics { contentDescription = "正在播放" },
        shape = EchoShapes.large,
        color = echoCardColor(p.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album art
            AsyncArtwork(
                uri = state.continueAlbumArtUri,
                title = state.continueTitle,
                subtitle = state.continueSubtitle,
                modifier = Modifier.size(56.dp),
                cornerRadius = 10.dp,
                fallbackTextSize = 16.sp,
                targetSize = 56.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
            // Track info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    state.continueDetail,
                    style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent
                )
                Text(
                    state.continueTitle,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.continueSubtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Progress bar
                if (state.continueProgress > 0f) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(EchoShapes.small)
                            .background(p.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.continueProgress)
                                .fillMaxHeight()
                                .background(p.accent)
                        )
                    }
                }
            }
            // Play/Pause button
            Surface(
                onClick = { actions.onContinue.run() },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = p.accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(
                        if (state.continuePlaying) EchoIconKind.Pause else EchoIconKind.Play,
                        Modifier.size(22.dp),
                        p.onAccent
                    )
                }
            }
        }
    }
}

// Stats grid (2x2 for mobile)

@Composable
private fun StatGrid(stats: List<HomeDashboardStatUiState>, actions: HomeDashboardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // First row: 2 cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            stats.take(2).forEachIndexed { index, stat ->
                StatCard(
                    stat,
                    actions.onOpenStat.getOrNull(index),
                    Modifier.weight(1f)
                )
            }
        }
        // Second row: 2 cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            stats.drop(2).take(2).forEachIndexed { index, stat ->
                StatCard(
                    stat,
                    actions.onOpenStat.getOrNull(index + 2),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(stat: HomeDashboardStatUiState, action: Runnable?, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { action?.run() },
        interactionSource = interaction,
        modifier = modifier
            .echoPressScale(interaction)
            .heightIn(min = 80.dp)
            .semantics { contentDescription = stat.label },
        shape = EchoShapes.medium,
        color = echoCardColor(p.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EchoIcon(iconForMode(stat.mode), Modifier.size(22.dp), p.accent)
            Column {
                Text(
                    stat.value,
                    style = EchoTypography.headline,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stat.label,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Recent activity section

@Composable
private fun RecentActivitySection(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "最近播放",
                style = EchoTypography.title,
                color = p.text
            )
            if (state.recent.isNotEmpty()) {
                Text(
                    "查看全部",
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = p.accent
                )
            }
        }
        // Content
        if (state.recent.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = EchoShapes.medium,
                color = echoCardColor(p.surface)
            ) {
                Text(
                    if (state.empty) "添加音乐后开始聆听" else "暂无最近播放",
                    style = EchoTypography.body,
                    color = p.muted,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                itemsIndexed(state.recent, key = { _, item -> item.id }) { index, item ->
                    CompactRecentCard(item, actions.onPlayRecent.getOrNull(index))
                }
            }
        }
    }
}

@Composable
private fun CompactRecentCard(item: HomeDashboardRecentUiState, action: Runnable?) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { action?.run() },
        interactionSource = interaction,
        modifier = Modifier
            .echoPressScale(interaction)
            .width(120.dp)
            .semantics { contentDescription = item.title },
        shape = EchoShapes.medium,
        color = p.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncArtwork(
                uri = item.albumArtUri,
                title = item.title,
                subtitle = item.subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = 6.dp,
                fallbackTextSize = 16.sp,
                targetSize = 104.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                style = EchoTypography.small,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeeklyRecapSection(state: HomeDashboardUiState) {
    val p = EchoTheme.colors()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section header
        Text(
            "本周回声",
            style = EchoTypography.title,
            color = p.text
        )
        // Content card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = EchoShapes.medium,
            color = echoCardColor(p.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Play count
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.weeklyPlays}",
                            style = EchoTypography.display.copy(fontSize = 28.sp),
                            color = p.text
                        )
                        Text(
                            "次播放",
                            style = EchoTypography.caption,
                            color = p.muted
                        )
                    }
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(p.border)
                    )
                    // Duration
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.weeklyDuration.replace(" ", ""),
                            style = EchoTypography.display.copy(fontSize = 28.sp),
                            color = p.text,
                            maxLines = 1
                        )
                        Text(
                            "聆听时长",
                            style = EchoTypography.caption,
                            color = p.muted
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Heatmap
                WeeklyHeatmap(state.heatmap, state.heatmapMonths, state.activeWeeks)
            }
        }
    }
}

@Composable
private fun WeeklyHeatmap(
    heatmap: List<HomeDashboardHeatmapDay>,
    months: List<HomeDashboardHeatmapMonth>,
    activeWeeks: Int
) {
    val p = EchoTheme.colors()
    // 12 natural-week columns, each with 7 rows (Mon..Sun), matching desktop.
    val weeks = if (heatmap.isEmpty()) {
        List(HEATMAP_WEEK_COLUMNS) { emptyList<HomeDashboardHeatmapDay>() }
    } else {
        heatmap.chunked(7)
    }
    val weekCount = weeks.size.coerceAtLeast(1)
    val maxCount = heatmap.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val dayLabelColumnWidth = 18.dp

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Month labels spanning their week columns.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(dayLabelColumnWidth))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                var cursor = 0
                months.sortedBy { it.weekIndex }.forEach { month ->
                    if (month.weekIndex > cursor) {
                        Spacer(Modifier.weight((month.weekIndex - cursor).toFloat()))
                    }
                    val span = month.span.coerceAtLeast(1)
                    Text(
                        month.label,
                        style = EchoTypography.small.copy(fontSize = 9.sp),
                        color = p.muted,
                        maxLines = 1,
                        modifier = Modifier.weight(span.toFloat())
                    )
                    cursor = month.weekIndex + span
                }
                if (cursor < weekCount) {
                    Spacer(Modifier.weight((weekCount - cursor).toFloat()))
                }
            }
        }

        // Day rows (Mon..Sun); label the Mon/Wed/Fri rows like desktop.
        val dayLabels = mapOf(0 to "一", 2 to "三", 4 to "五")
        for (dayIndex in 0 until 7) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(dayLabelColumnWidth)) {
                    dayLabels[dayIndex]?.let { label ->
                        Text(
                            label,
                            style = EchoTypography.small.copy(fontSize = 9.sp),
                            color = p.muted
                        )
                    }
                }
                weeks.forEach { week ->
                    val day = week.getOrNull(dayIndex)
                    HeatCell(heatLevel(day, maxCount), future = day?.future == true)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            if (activeWeeks > 0) "$activeWeeks 周活跃" else "开始播放以记录活跃",
            style = EchoTypography.small,
            color = p.muted
        )
    }
}

/** Map a day to a 0..4 intensity level using count/maxCount ratio (desktop parity). */
private fun heatLevel(day: HomeDashboardHeatmapDay?, maxCount: Int): Int {
    val count = day?.count ?: 0
    if (count <= 0) return 0
    val ratio = count.toFloat() / maxCount.toFloat()
    return when {
        ratio >= 0.8f -> 4
        ratio >= 0.55f -> 3
        ratio >= 0.25f -> 2
        else -> 1
    }
}

@Composable
private fun RowScope.HeatCell(level: Int, future: Boolean) {
    val p = EchoTheme.colors()
    val background = when {
        future -> p.surfaceVariant.copy(alpha = 0.25f)
        level <= 0 -> p.surfaceVariant.copy(alpha = 0.5f)
        // levels 1..4 -> increasing accent intensity
        else -> p.accent.copy(alpha = 0.2f + (level / 4f) * 0.7f)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(EchoShapes.small)
            .background(background)
    )
}

@Composable
private fun WaveProgress(progress: Float) {
    val p = EchoTheme.colors()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val bars = 28
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val activeBars = (bars * progress.coerceIn(0f, 1f)).toInt()
        for (i in 0 until bars) {
            val seed = ((i * 37) % 11) / 10f
            val barHeight = size.height * (0.28f + seed * 0.58f)
            val color = if (i <= activeBars) p.accent else p.border
            val left = i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}

// Recommendations (每日推荐 / 心动推荐)

@Composable
private fun RecommendationCards(actions: HomeDashboardActions, enabled: Boolean = true) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecommendationCard(
            title = "每日推荐",
            subtitle = if (enabled) "每天为你精选" else "需要登录",
            icon = EchoIconKind.Sparkle,
            modifier = Modifier.weight(1f),
            dimmed = !enabled,
            onClick = { if (enabled) actions.onDailyRecommend.run() else actions.onConnectStreaming.run() }
        )
        RecommendationCard(
            title = "心动推荐",
            subtitle = if (enabled) "根据喜好智能播放" else "需要登录",
            icon = EchoIconKind.Heart,
            modifier = Modifier.weight(1f),
            dimmed = !enabled,
            onClick = { if (enabled) actions.onHeartbeatRecommend.run() else actions.onConnectStreaming.run() }
        )
    }
}

@Composable
private fun RecommendationCard(
    title: String,
    subtitle: String,
    icon: EchoIconKind,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (dimmed) 0.5f else 1f
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .echoPressScale(interaction)
            .alpha(alpha)
            .semantics { contentDescription = title },
        shape = EchoShapes.medium,
        color = echoCardColor(p.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoIcon(icon, Modifier.size(22.dp), p.accent)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = EchoTypography.small,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun iconForMode(mode: String): EchoIconKind = when (mode) {
    "albums" -> EchoIconKind.Collections
    "artists" -> EchoIconKind.Artist
    "folders" -> EchoIconKind.Folder
    else -> EchoIconKind.Library
}

@Composable
private fun StreamingGuideCard(onConnect: () -> Unit) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onConnect,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .semantics { contentDescription = "连接流媒体账号" },
        shape = EchoShapes.medium,
        color = p.accent.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EchoIcon(EchoIconKind.Action, Modifier.size(24.dp), p.accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "连接流媒体账号",
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading
                )
                Text(
                    "解锁每日推荐和心动推荐",
                    style = EchoTypography.small,
                    color = p.muted
                )
            }
            Text(
                "去连接",
                style = EchoTypography.label,
                color = p.accent
            )
        }
    }
}
