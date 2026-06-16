package app.echo.next.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SettingsMetric(val label: String, val value: String)
data class SettingsAction(val label: String, val onClick: Runnable, val description: String = "")

class SettingsListScrollState(
    var firstVisibleItemIndex: Int = 0,
    var firstVisibleItemScrollOffset: Int = 0
) {
    private var ignoreNextSave: Boolean = false

    fun save(listState: LazyListState) {
        if (ignoreNextSave) {
            ignoreNextSave = false
            return
        }
        firstVisibleItemIndex = listState.firstVisibleItemIndex
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
    }

    fun scrollToTop() {
        firstVisibleItemIndex = 0
        firstVisibleItemScrollOffset = 0
        ignoreNextSave = true
    }
}

@Composable
fun SettingsScreen(
    title: String,
    metrics: List<SettingsMetric>,
    actions: List<SettingsAction>,
    scrollState: SettingsListScrollState
) {
    val titleBackAction = actions.firstOrNull { isBackAction(it.label) }
    val visibleActions = if (titleBackAction != null) actions.drop(1) else actions
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollState.firstVisibleItemIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
    DisposableEffect(listState) {
        onDispose {
            scrollState.save(listState)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(
                title,
                backLabel = titleBackAction?.label,
                onBack = titleBackAction?.onClick
            )
        }
        itemsIndexed(
            items = visibleActions,
            key = { index, action -> "action:${action.label}:$index" }
        ) { index, action ->
            SettingsActionButton(action, Modifier.echoEnter(index.coerceAtMost(8))) {
                scrollState.save(listState)
                action.onClick.run()
            }
        }
        if (visibleActions.isNotEmpty() && metrics.isNotEmpty()) {
            item(key = "metrics-spacer") {
                Spacer(Modifier.height(6.dp))
            }
        }
        itemsIndexed(
            items = metrics,
            key = { index, metric -> "metric:${metric.label}:$index" }
        ) { _, metric ->
            SettingsMetricRow(metric)
        }
    }
}

@Composable
private fun SettingsActionButton(action: SettingsAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = action.label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(
                kind = iconForAction(action.label),
                modifier = Modifier.size(22.dp),
                color = p.accent
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    action.label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (action.description.isNotBlank()) {
                    Text(
                        action.description,
                        style = EchoTypography.caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun SettingsMetricRow(metric: SettingsMetric) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                metric.label,
                style = EchoTypography.bodyMedium,
                color = p.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                metric.value,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private fun iconForAction(label: String): EchoIconKind {
    fun has(vararg keys: String) = keys.any { label.contains(it, ignoreCase = true) }
    if (isBackAction(label)) {
        return EchoIconKind.Back
    }
    return when {
        // Navigation (startsWith avoids matching "playback")
        label.startsWith("Back", ignoreCase = true) || label.contains("返回") -> EchoIconKind.Back
        // Appearance / theme / accent / language
        has("Accent", "强调色") -> EchoIconKind.Swatch
        has("Appearance", "Theme", "外观", "主题") -> EchoIconKind.Palette
        has("Language", "语言") -> EchoIconKind.Language
        // Playback controls
        has("speed", "播放速度", "速度") || label.endsWith("x") || label.endsWith("X") -> EchoIconKind.Gauge
        has("volume", "音量") || label.endsWith("%") -> EchoIconKind.Volume
        has("sleep", "timer", "睡眠", "定时") || label.endsWith("min") || label.contains("分钟") -> EchoIconKind.Timer
        has("lyrics", "offset", "歌词", "偏移") -> EchoIconKind.Lyrics
        // Verb-based actions (checked before the broad network/stream category
        // so "删除串流"/"播放串流" get their action icon, not the network icon)
        has("clear", "delete", "cancel", "disable", "清空", "删除", "取消", "关闭") -> EchoIconKind.Delete
        has("scan", "reload", "sync", "扫描", "重新加载", "同步") -> EchoIconKind.Sync
        has("import", "export", "导入", "导出") -> EchoIconKind.Import
        has("play", "播放") -> EchoIconKind.Play
        has("browse", "浏览") -> EchoIconKind.Collections
        has("manage", "edit", "管理", "编辑") -> EchoIconKind.Edit
        // Network / sources (gateway, remote, and the bare "Streaming" entry)
        has("WebDAV") -> EchoIconKind.Folder
        has("remote", "network", "gateway", "远程", "网络", "网关", "流媒体") ||
            label.trim().equals("Streaming", ignoreCase = true) || label.trim() == "串流" -> EchoIconKind.Network
        // Library
        has("folder", "文件夹") -> EchoIconKind.Folder
        has("library", "song", "track", "曲库", "歌曲", "曲目") -> EchoIconKind.Library
        // Create / enable
        has("add", "enable", "new", "添加", "开启", "新建") -> EchoIconKind.Action
        else -> EchoIconKind.Action
    }
}

private fun isBackAction(label: String): Boolean =
    label.startsWith("Back", ignoreCase = true) ||
        label.contains("\u8fd4\u56de") ||
        label.contains("杩斿洖")
