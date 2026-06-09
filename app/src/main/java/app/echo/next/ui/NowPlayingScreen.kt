package app.echo.next.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R

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
    val lyrics: List<LyricUiLine>
)

data class LyricUiLine(val text: String, val active: Boolean)

class NowPlayingController(context: Context, initialState: NowPlayingUiState) {
    private val state: MutableState<NowPlayingUiState> = mutableStateOf(initialState)

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                NowPlayingScreen(state.value)
            }
        }
    }

    fun updateState(nextState: NowPlayingUiState) {
        state.value = nextState
    }
}

@Composable
private fun NowPlayingScreen(state: NowPlayingUiState) {
    val p = EchoTheme.colors()
    val activeLyricIndex = state.lyrics.indexOfFirst { it.active }

    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.sectionSpacing)
    ) {
        item(key = "page-title") {
            EchoPageTitle(state.pageTitle.ifBlank { "Now" })
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
                    AlbumArtHero(state.albumArtUri, state.title, state.subtitle)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        state.title,
                        style = EchoTypography.headline,
                        color = p.heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (state.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.subtitle,
                            style = EchoTypography.body,
                            color = p.muted,
                            maxLines = 2,
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
private fun AlbumArtHero(uri: Uri?, title: String, subtitle: String) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .size(EchoMobileLayoutMetrics.nowPlayingArtworkSize)
            .clip(EchoShapes.large),
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
    Surface(
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
