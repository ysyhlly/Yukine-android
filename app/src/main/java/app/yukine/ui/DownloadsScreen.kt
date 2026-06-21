package app.yukine.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yukine.DownloadsUiState
import app.yukine.TrackDownloadItem
import app.yukine.TrackDownloadStatus
import java.util.Locale

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    directoryLabel: String = "",
    onUseMusicDirectory: () -> Unit = {},
    onUseDownloadsDirectory: () -> Unit = {},
    onChooseDirectory: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle("下载管理")
        }
        item(key = "controls") {
            DownloadSettingsCard(
                directoryLabel = directoryLabel.ifBlank { "音乐/Yukine" },
                onUseMusicDirectory = onUseMusicDirectory,
                onUseDownloadsDirectory = onUseDownloadsDirectory,
                onChooseDirectory = onChooseDirectory
            )
        }
        if (state.active.isEmpty() && state.finished.isEmpty()) {
            item(key = "empty") {
                EchoStateCard(
                    title = "暂无下载任务",
                    description = "在歌单或歌曲列表里点击下载后，进度会显示在这里。",
                    icon = EchoIconKind.Import
                )
            }
            return@LazyColumn
        }
        if (state.active.isNotEmpty()) {
            item(key = "active-title") {
                EchoSectionTitle("待下载 / 下载中")
            }
            items(state.active, key = { "active:${it.downloadId}" }) { item ->
                DownloadItemCard(item)
            }
        }
        if (state.finished.isNotEmpty()) {
            item(key = "finished-title") {
                EchoSectionTitle("已下载")
            }
            items(state.finished, key = { "finished:${it.downloadId}" }) { item ->
                DownloadItemCard(item)
            }
        }
    }
}

@Composable
private fun DownloadSettingsCard(
    directoryLabel: String,
    onUseMusicDirectory: () -> Unit,
    onUseDownloadsDirectory: () -> Unit,
    onChooseDirectory: () -> Unit
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(EchoIconKind.Folder, Modifier.size(22.dp), p.accent)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "保存目录：$directoryLabel",
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "默认目录使用系统下载服务；自定义目录会调用安卓目录管理器授权保存。",
                        style = EchoTypography.caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadDirectoryButton("音乐", directoryLabel.startsWith("音乐"), onUseMusicDirectory)
                DownloadDirectoryButton("下载", directoryLabel.startsWith("下载"), onUseDownloadsDirectory)
                DownloadDirectoryButton("选择目录", directoryLabel.startsWith("自定义"), onChooseDirectory)
            }
        }
    }
}

@Composable
private fun DownloadDirectoryButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = EchoShapes.small,
        color = if (selected) p.accentSoft.copy(alpha = 0.66f) else p.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) {
                EchoIcon(EchoIconKind.Check, Modifier.size(14.dp), p.accent)
            }
            Text(
                label,
                style = EchoTypography.label,
                color = if (selected) p.accent else p.text
            )
        }
    }
}

@Composable
private fun DownloadItemCard(item: TrackDownloadItem) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(
                    kind = iconForStatus(item.status),
                    modifier = Modifier.size(22.dp),
                    color = colorForStatus(item.status)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.artist.isNotBlank()) {
                        Text(
                            item.artist,
                            style = EchoTypography.caption,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DownloadProgressBadge(item)
            }
            LinearProgressIndicator(
                progress = { progressValue(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = colorForStatus(item.status),
                trackColor = p.surfaceVariant.copy(alpha = 0.36f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sizeLabel(item), style = EchoTypography.small, color = p.muted)
                Text("${item.progressPercent.coerceIn(0, 100)}%", style = EchoTypography.small, color = p.muted)
            }
            if (item.status == TrackDownloadStatus.Finished && item.localUri.isNotBlank()) {
                Text(
                    item.localUri,
                    style = EchoTypography.small,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressBadge(item: TrackDownloadItem) {
    val p = EchoTheme.colors()
    val color = colorForStatus(item.status)
    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progressValue(item) },
            modifier = Modifier.size(42.dp),
            color = color,
            trackColor = p.surfaceVariant.copy(alpha = 0.34f),
            strokeWidth = 4.dp
        )
        Text(
            progressBadgeText(item),
            style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1
        )
    }
}

private fun progressValue(item: TrackDownloadItem): Float =
    if (item.status == TrackDownloadStatus.Finished) {
        1f
    } else {
        item.progressPercent.coerceIn(0, 100) / 100f
    }

private fun progressBadgeText(item: TrackDownloadItem): String = when (item.status) {
    TrackDownloadStatus.Finished -> "100%"
    TrackDownloadStatus.Failed -> "失败"
    TrackDownloadStatus.Paused -> "暂停"
    TrackDownloadStatus.Pending -> "等待"
    else -> "${item.progressPercent.coerceIn(0, 100)}%"
}

@Composable
private fun colorForStatus(status: TrackDownloadStatus): Color {
    val p = EchoTheme.colors()
    return when (status) {
        TrackDownloadStatus.Finished -> p.accent
        TrackDownloadStatus.Failed -> Color(0xFFE06666)
        TrackDownloadStatus.Paused -> Color(0xFFE0A84F)
        else -> p.text
    }
}

private fun iconForStatus(status: TrackDownloadStatus): EchoIconKind = when (status) {
    TrackDownloadStatus.Finished -> EchoIconKind.Check
    TrackDownloadStatus.Failed -> EchoIconKind.Remove
    TrackDownloadStatus.Paused -> EchoIconKind.Pause
    else -> EchoIconKind.Import
}

private fun sizeLabel(item: TrackDownloadItem): String {
    val downloaded = bytesLabel(item.bytesDownloaded)
    val total = if (item.totalBytes > 0L) bytesLabel(item.totalBytes) else "未知大小"
    return "$downloaded / $total"
}

private fun bytesLabel(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
