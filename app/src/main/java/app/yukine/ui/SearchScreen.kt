package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.MainActivityStreamingState
import app.yukine.SearchViewModel
import app.yukine.TrackDownloadItem
import app.yukine.model.Track
import app.yukine.streaming.StreamingTrack

fun interface UnifiedSearchQueryAction {
    fun run(query: String)
}

fun interface UnifiedLocalTrackAction {
    fun run(track: Track)
}

data class UnifiedSearchActions(
    val onQueryChange: UnifiedSearchQueryAction,
    val onSearch: UnifiedSearchQueryAction,
    val onPlayLocalTrack: UnifiedLocalTrackAction,
    val onPlayStreamingTrack: StreamingTrackAction,
    val onLoadMoreStreaming: Runnable,
    val onExit: Runnable
) {
    companion object {
        @JvmStatic
        fun empty(): UnifiedSearchActions = UnifiedSearchActions(
            onQueryChange = UnifiedSearchQueryAction { _ -> },
            onSearch = UnifiedSearchQueryAction { _ -> },
            onPlayLocalTrack = UnifiedLocalTrackAction { _ -> },
            onPlayStreamingTrack = StreamingTrackAction { _ -> },
            onLoadMoreStreaming = Runnable {},
            onExit = Runnable {}
        )
    }
}

@Composable
fun UnifiedSearchScreen(
    state: SearchViewModel,
    streamingState: MainActivityStreamingState,
    actions: UnifiedSearchActions,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val searchState by state.uiState.collectAsState()
    val query = searchState.query
    val onlineTracks = streamingState.searchResult?.tracks.orEmpty()
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val currentOnExit by rememberUpdatedState(actions.onExit)

    DisposableEffect(Unit) {
        onDispose {
            currentOnExit.run()
        }
    }

    CollapsibleSearchHeader(
        header = {
            SearchInput(
                query = query,
                onQueryChange = { actions.onQueryChange.run(it) },
                onSearch = { actions.onSearch.run(query) },
                activeDownload = activeDownload,
                playbackQuality = playbackQuality,
                audioMotion = audioMotion
            )
        }
    ) { contentModifier, _ ->
        LazyColumn(
            state = listState,
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("header") {
                EchoPageTitle("搜索音乐")
            }

            item("local-title") {
                SectionTitle("本地曲库", searchState.localTracks.size)
            }
            if (query.isBlank()) {
                item("local-empty-before") {
                    EmptyHint("输入歌名、歌手或专辑名，同时搜索本地和在线音乐。")
                }
            } else if (searchState.localTracks.isEmpty() && searchState.searched) {
                item("local-empty") { EmptyHint("本地没有找到匹配歌曲") }
            } else {
                items(searchState.localTracks, key = { it.id }) { track ->
                    LocalTrackResultRow(track) { actions.onPlayLocalTrack.run(track) }
                }
            }

            item("online-title") {
                SectionTitle("在线音乐", onlineTracks.size)
            }
            when {
                query.isBlank() -> item("online-empty-before") {
                    EmptyHint("按搜索后会使用当前已连接的流媒体账号查找在线歌曲。")
                }
                streamingState.loading -> item("online-loading") { EmptyHint("正在搜索在线音乐...") }
                !streamingState.errorMessage.isNullOrBlank() -> item("online-error") {
                    EmptyHint(streamingState.errorMessage ?: "在线搜索失败")
                }
                onlineTracks.isEmpty() && searchState.searched -> item("online-empty") {
                    EmptyHint("在线音乐没有找到匹配歌曲")
                }
                else -> {
                    items(onlineTracks, key = { it.stableKey }) { track ->
                        StreamingTrackResultRow(track) { actions.onPlayStreamingTrack.run(track) }
                    }
                    if (streamingState.searchResult?.hasMore == true) {
                        item("online-more") {
                            SearchActionButton("加载更多", EchoIconKind.Sync) {
                                actions.onLoadMoreStreaming.run()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .echoGlassLayer(p, EchoShapes.medium),
            contentAlignment = Alignment.Center
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxSize(),
                singleLine = true,
                placeholder = {
                    Text("搜索歌曲、歌手、专辑", style = EchoTypography.bodyMedium, color = p.muted)
                },
                leadingIcon = { EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent) },
                textStyle = EchoTypography.bodyMedium.copy(color = p.text, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    cursorColor = p.accent
                )
            )
        }
        Surface(
            onClick = onSearch,
            shape = EchoShapes.medium,
            color = Color.Transparent,
            modifier = Modifier
                .height(48.dp)
                .echoGlassLayer(p, EchoShapes.medium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent)
                Spacer(Modifier.width(6.dp))
                Text("搜索", style = EchoTypography.label, color = p.accent)
            }
        }
        YukineDownloadOrb(
            item = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = EchoTypography.title, color = p.heading)
        Spacer(Modifier.width(8.dp))
        Text("$count 首", style = EchoTypography.caption, color = p.muted)
    }
}

@Composable
private fun LocalTrackResultRow(track: Track, onClick: () -> Unit) {
    ResultRow(
        title = track.title,
        subtitle = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" / "),
        coverUri = track.albumArtUri,
        onClick = onClick
    )
}

@Composable
private fun StreamingTrackResultRow(track: StreamingTrack, onClick: () -> Unit) {
    ResultRow(
        title = track.title,
        subtitle = listOf(track.artist, track.album.orEmpty()).filter { it.isNotBlank() }.joinToString(" / "),
        coverUri = (track.coverThumbUrl ?: track.coverUrl)?.let(Uri::parse),
        onClick = onClick
    )
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    coverUri: Uri?,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncArtwork(
                uri = coverUri,
                title = title,
                subtitle = subtitle,
                modifier = Modifier.size(52.dp),
                cornerRadius = 8.dp,
                fallbackTextSize = 14.sp,
                targetSize = 52.dp,
                backgroundColor = p.surfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title.ifBlank { "未知歌曲" },
                    style = EchoTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle.ifBlank { "未知歌手" },
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EchoIcon(EchoIconKind.Play, Modifier.size(22.dp), p.accent)
        }
    }
}

@Composable
private fun SearchActionButton(label: String, icon: EchoIconKind, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        shape = EchoShapes.medium,
        color = Color.Transparent,
        modifier = Modifier.echoGlassLayer(p, EchoShapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            EchoIcon(icon, Modifier.size(18.dp), p.accent)
            Spacer(Modifier.width(8.dp))
            Text(label, style = EchoTypography.label, color = p.heading)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, style = EchoTypography.body, color = p.muted)
    }
}
