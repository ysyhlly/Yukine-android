package app.yukine.ui

import android.net.Uri
import android.os.SystemClock
import app.yukine.TrackDownloadItem
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R
import kotlinx.coroutines.delay

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
    val nextTitle: String = "",
    val nextSubtitle: String = "",
    val nextAlbumArtUri: Uri? = null,
    val stats: List<HomeDashboardStatUiState> = emptyList(),
    val recent: List<HomeDashboardRecentUiState> = emptyList(),
    val recentTabIndex: Int = 0,
    val todayListeningDuration: String = "0 分钟",
    val todayListeningPoints: List<HomeDashboardListeningPoint> = emptyList(),
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

data class HomeDashboardListeningPoint(
    val hour: Int,
    val durationMs: Long,
    val future: Boolean = false
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
    val onConnectStreaming: Runnable = Runnable { },
    val onSearch: Runnable = Runnable { },
    val onNext: Runnable = Runnable { }
)

internal const val PLAYBACK_CARD_STABILITY_MILLIS = 1_200L

internal data class HomePlaybackCardState(
    val title: String,
    val subtitle: String,
    val detail: String,
    val albumArtUri: Uri?,
    val progress: Float
) {
    fun action(actions: HomeDashboardActions): Runnable = actions.onContinue
}

internal class HomePlaybackCardStabilizer(
    private val settleMillis: Long = PLAYBACK_CARD_STABILITY_MILLIS,
    initial: HomePlaybackCardState? = null
) {
    var displayed: HomePlaybackCardState? = initial
        private set
    private var pending: HomePlaybackCardState? = null
    private var hasPending: Boolean = false
    private var pendingSinceMillis: Long = 0L

    fun update(candidate: HomePlaybackCardState?, nowMillis: Long): HomePlaybackCardState? {
        if (displayed == null) {
            displayed = candidate
            pending = null
            hasPending = false
            return displayed
        }
        if (candidate == displayed) {
            pending = null
            hasPending = false
            return displayed
        }
        if (!hasPending || candidate != pending) {
            pending = candidate
            hasPending = true
            pendingSinceMillis = nowMillis
            return displayed
        }
        if (nowMillis - pendingSinceMillis >= settleMillis) {
            displayed = candidate
            pending = null
            hasPending = false
        }
        return displayed
    }
}

internal fun HomeDashboardUiState.playbackCardState(): HomePlaybackCardState? {
    if (nextTitle.isBlank()) return null
    return HomePlaybackCardState(
        title = nextTitle,
        subtitle = nextSubtitle,
        detail = "即将播放",
        albumArtUri = nextAlbumArtUri,
        progress = 0f
    )
}

@Composable
private fun rememberStablePlaybackCard(
    candidate: HomePlaybackCardState?
): HomePlaybackCardState? {
    val stabilizer = remember { HomePlaybackCardStabilizer(initial = candidate) }
    var displayed by remember { mutableStateOf(stabilizer.displayed) }
    LaunchedEffect(candidate) {
        displayed = stabilizer.update(candidate, SystemClock.elapsedRealtime())
        if (displayed != candidate) {
            delay(PLAYBACK_CARD_STABILITY_MILLIS)
            displayed = stabilizer.update(candidate, SystemClock.elapsedRealtime())
        }
    }
    return displayed
}

@Composable
fun HomeDashboardScreen(
    state: HomeDashboardUiState,
    actions: HomeDashboardActions,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    layout: HomeDashboardLayout = HomeDashboardLayout.Classic
) {
    val playbackCard = rememberStablePlaybackCard(state.playbackCardState())
    CollapsibleSearchHeader(
        header = { HomeSearchCard(actions, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 18.dp, top = 6.dp, end = 18.dp, bottom = echoPageBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (layout == HomeDashboardLayout.Classic) {
                item("classic-hero") {
                    Box(Modifier.echoEnter(0)) { ClassicHeroSection(state, actions, playbackCard) }
                }
                item("classic-recommendations") {
                    Box(Modifier.echoEnter(1)) {
                        ClassicRecommendationCards(actions, enabled = state.streamingConnected)
                    }
                }
                item("classic-today-listening") {
                    Box(Modifier.echoEnter(2)) { TodayListeningSection(state) }
                }
                item("classic-recent") {
                    Box(Modifier.echoEnter(3)) { RecentActivitySection(state, actions) }
                }
                item("classic-weekly") {
                    Box(Modifier.echoEnter(4)) { WeeklyRecapSection(state) }
                }
            } else {
                item("content-hero") {
                    Box(Modifier.echoEnter(0)) { ContentHeroSection(state, actions, playbackCard) }
                }
                item("content-recent") {
                    Box(Modifier.echoEnter(1)) { RecentActivitySection(state, actions) }
                }
                item("content-recommendations") {
                    Box(Modifier.echoEnter(2)) {
                        ContentRecommendationCards(actions, enabled = state.streamingConnected)
                    }
                }
                item("content-stats") {
                    Box(Modifier.echoEnter(3)) { StatGrid(state.stats, actions) }
                }
                item("content-weekly") {
                    Box(Modifier.echoEnter(4)) { WeeklyRecapSection(state) }
                }
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
private fun ClassicHeroSection(
    state: HomeDashboardUiState,
    actions: HomeDashboardActions,
    playbackCard: HomePlaybackCardState?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (playbackCard != null) {
            ClassicContinueCard(playbackCard, state.continuePlaying, actions)
        }
        ClassicHeroPanel(state, actions)
    }
}

@Composable
private fun ClassicHeroPanel(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(p.accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "今日回声",
                modifier = Modifier.weight(1f),
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent
            )
        }
        Text(
            state.heroTitle,
            style = EchoTypography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            state.heroSubtitle.ifBlank { "接上最近播放，或者从最近入库里挑一张封面开始。" },
            style = EchoTypography.body,
            color = p.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        ClassicHeroActionButtons(state, actions)
    }
}

@Composable
private fun ClassicHeroActionButtons(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        item {
            Surface(
                onClick = { actions.onContinue.run() },
                modifier = Modifier
                    .width(116.dp)
                    .height(42.dp)
                    .semantics {
                        contentDescription = if (state.continuePlaying) "暂停播放" else "继续播放"
                    },
                shape = EchoShapes.medium,
                color = p.accent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    EchoIcon(
                        if (state.continuePlaying) EchoIconKind.Pause else EchoIconKind.Play,
                        Modifier.size(18.dp),
                        p.onAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                    if (state.continuePlaying) "暂停播放" else "继续播放",
                        style = EchoTypography.label,
                        color = p.onAccent
                    )
                }
            }
        }
        item { HeroSecondaryButton("队列", EchoIconKind.Queue) { actions.onViewQueue.run() } }
        item { HeroSecondaryButton("随机", EchoIconKind.Shuffle) { actions.onShuffleAll.run() } }
        item { HeroSecondaryButton("搜索", EchoIconKind.Search) { actions.onSearch.run() } }
        item { HeroSecondaryButton("刷新", EchoIconKind.Sync) { actions.onRefresh.run() } }
    }
}

@Composable
private fun ClassicContinueCard(
    card: HomePlaybackCardState,
    playing: Boolean,
    actions: HomeDashboardActions
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { card.action(actions).run() },
        interactionSource = interaction,
        modifier = Modifier
            .echoPressScale(interaction)
            .fillMaxWidth()
            .echoGaussianBackdrop(p, EchoShapes.large)
            .semantics { contentDescription = card.detail },
        shape = EchoShapes.large,
        color = echoCardColor(p.surface),
        border = BorderStroke(1.dp, p.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncArtwork(
                uri = card.albumArtUri,
                title = card.title,
                subtitle = card.subtitle,
                modifier = Modifier.size(56.dp),
                cornerRadius = 10.dp,
                fallbackTextSize = 16.sp,
                targetSize = 56.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    card.detail,
                    style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent
                )
                Text(
                    card.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    card.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.progress > 0f) {
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
                                .fillMaxWidth(card.progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(p.accent)
                        )
                    }
                }
            }
            Surface(
                onClick = { card.action(actions).run() },
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (playing) "暂停播放" else "继续播放"
                    },
                shape = CircleShape,
                color = p.accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(
                        if (playing) EchoIconKind.Pause else EchoIconKind.Play,
                        Modifier.size(22.dp),
                        p.onAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentHeroSection(
    state: HomeDashboardUiState,
    actions: HomeDashboardActions,
    playbackCard: HomePlaybackCardState?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ContentHeroPanel(state, actions)
        if (playbackCard != null) {
            ContentNowPlayingCard(playbackCard, state.continuePlaying, actions)
        }
        ContentHeroActionButtons(actions)
    }
}

@Composable
private fun ContentHeroPanel(state: HomeDashboardUiState, actions: HomeDashboardActions) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                state.heroTitle,
                modifier = Modifier.weight(1f),
                style = EchoTypography.headline.copy(fontSize = 22.sp, lineHeight = 27.sp),
                color = p.heading,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = { actions.onRefresh.run() },
                modifier = Modifier
                    .size(34.dp)
                    .semantics { contentDescription = "刷新" },
                shape = CircleShape,
                color = p.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.Sync, Modifier.size(16.dp), p.accent)
                }
            }
        }
        Text(
            state.heroSubtitle.ifBlank { "继续最近播放，或从最近入库里挑一张封面开始。" },
            style = EchoTypography.caption,
            color = p.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContentHeroActionButtons(actions: HomeDashboardActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeroSecondaryButton(
            label = "查看队列",
            icon = EchoIconKind.Queue,
            modifier = Modifier.weight(1f)
        ) { actions.onViewQueue.run() }
        HeroSecondaryButton(
            label = "随机播放",
            icon = EchoIconKind.Shuffle,
            modifier = Modifier.weight(1f)
        ) { actions.onShuffleAll.run() }
    }
}

@Composable
private fun HeroSecondaryButton(
    label: String,
    icon: EchoIconKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = echoCardColor(p.surface),
        border = BorderStroke(1.dp, p.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            EchoIcon(icon, Modifier.size(17.dp), p.accent)
            Spacer(Modifier.width(8.dp))
            Text(label, style = EchoTypography.label, color = p.text)
        }
    }
}

// Now playing card (full width for mobile)

@Composable
private fun ContentNowPlayingCard(
    card: HomePlaybackCardState,
    playing: Boolean,
    actions: HomeDashboardActions
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { card.action(actions).run() },
        interactionSource = interaction,
        modifier = Modifier
            .echoPressScale(interaction)
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGaussianBackdrop(p, EchoShapes.large)
            .semantics { contentDescription = card.detail },
        shape = EchoShapes.large,
        color = echoCardColor(p.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    card.detail,
                    style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent
                )
                Text(
                    card.title,
                    style = EchoTypography.title,
                    color = p.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    card.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { card.action(actions).run() },
                        modifier = Modifier
                            .requiredSize(width = 48.dp, height = 30.dp)
                            .semantics {
                                contentDescription = if (playing) "暂停播放" else "继续播放"
                            },
                        shape = CircleShape,
                        color = p.accent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            EchoIcon(
                                if (playing) EchoIconKind.Pause else EchoIconKind.Play,
                                Modifier.size(18.dp),
                                p.onAccent
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(EchoShapes.small)
                            .background(p.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(card.progress.coerceAtLeast(0.04f))
                                .fillMaxHeight()
                                .background(p.accent)
                        )
                    }
                }
            }
            AsyncArtwork(
                uri = card.albumArtUri,
                title = card.title,
                subtitle = card.subtitle,
                modifier = Modifier.size(width = 116.dp, height = 108.dp),
                cornerRadius = 16.dp,
                fallbackTextSize = 20.sp,
                targetSize = 116.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
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
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGaussianBackdrop(p, EchoShapes.medium)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .echoGaussianBackdrop(p, EchoShapes.medium),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            .width(88.dp)
            .echoFloatingLayer(p, EchoShapes.medium)
            .semantics { contentDescription = item.title },
        shape = EchoShapes.medium,
        color = p.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(7.dp)) {
            AsyncArtwork(
                uri = item.albumArtUri,
                title = item.title,
                subtitle = item.subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = 6.dp,
                fallbackTextSize = 16.sp,
                targetSize = 74.dp,
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
            modifier = Modifier
                .fillMaxWidth()
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGaussianBackdrop(p, EchoShapes.medium),
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
private fun ClassicRecommendationCards(actions: HomeDashboardActions, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
private fun ContentRecommendationCards(actions: HomeDashboardActions, enabled: Boolean = true) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("今天听什么", style = EchoTypography.title, color = p.text)
        RecommendationCard(
            title = "每日推荐",
            subtitle = if (enabled) "每天为你精选" else "需要登录",
            icon = EchoIconKind.Sparkle,
            modifier = Modifier.fillMaxWidth(),
            dimmed = !enabled,
            onClick = { if (enabled) actions.onDailyRecommend.run() else actions.onConnectStreaming.run() }
        )
        RecommendationCard(
            title = "心动推荐",
            subtitle = if (enabled) "根据喜好智能播放" else "需要登录",
            icon = EchoIconKind.Heart,
            modifier = Modifier.fillMaxWidth(),
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
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .echoPressScale(interaction)
            .echoGaussianBackdrop(p, EchoShapes.medium)
            .semantics { contentDescription = title },
        shape = EchoShapes.medium,
        color = echoCardColor(if (dimmed) p.surfaceVariant else p.surface),
        border = BorderStroke(1.dp, p.border)
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
