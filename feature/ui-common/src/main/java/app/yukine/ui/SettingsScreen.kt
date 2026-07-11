package app.yukine.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import app.yukine.TrackDownloadItem

data class SettingsMetric(
    val label: String,
    val value: String,
    val compact: Boolean = false
)
enum class SettingsActionStyle {
    Default,
    Navigation,
    Toggle,
    Choice,
    Destructive
}

data class SettingsAction(
    val label: String,
    val onClick: Runnable,
    val description: String = "",
    val value: String = "",
    val style: SettingsActionStyle = SettingsActionStyle.Default,
    val checked: Boolean = false,
    val section: String = "",
    val isBack: Boolean = false
)

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
    scrollState: SettingsListScrollState,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val titleBackAction = actions.firstOrNull { it.isBack || isBackAction(it.label) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EchoPageTitle(
                    title,
                    modifier = Modifier.weight(1f),
                    backLabel = titleBackAction?.label,
                    onBack = titleBackAction?.onClick
                )
                YukineDownloadOrb(
                    item = activeDownload,
                    playbackQuality = playbackQuality,
                    audioMotion = audioMotion,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        if (metrics.isNotEmpty()) {
            item(key = "overview") {
                SettingsOverviewCard(metrics)
            }
        }
        itemsIndexed(
            items = visibleActions,
            key = { index, action -> "action:${action.label}:$index" }
        ) { index, action ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val previousSection = visibleActions.getOrNull(index - 1)?.section.orEmpty()
                if (action.section.isNotBlank() && action.section != previousSection) {
                    Text(
                        text = action.section,
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = EchoTheme.colors().muted,
                        modifier = Modifier.padding(start = 4.dp, top = if (index == 0) 2.dp else 8.dp)
                    )
                }
                SettingsActionButton(action, Modifier.echoEnter(index.coerceAtMost(8))) {
                    scrollState.save(listState)
                    action.onClick.run()
                }
            }
        }
    }
}

@Composable
private fun SettingsActionButton(action: SettingsAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val cardModifier = modifier
        .fillMaxWidth()
        .echoGlassLayer(p, EchoShapes.medium)

    if (action.style == SettingsActionStyle.Toggle) {
        Surface(
            modifier = cardModifier,
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            SettingsActionRow(action, onClick)
        }
    } else {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = cardModifier
                .echoPressScale(interaction)
                .semantics { contentDescription = action.label },
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            SettingsActionRow(action, onClick)
        }
    }
}

@Composable
private fun SettingsActionRow(action: SettingsAction, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoIcon(
            kind = iconForSettingsAction(action),
            modifier = Modifier.size(22.dp),
            color = if (action.style == SettingsActionStyle.Destructive) p.muted else p.accent
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
        SettingsActionTrailing(action, onClick)
    }
}

@Composable
private fun SettingsActionTrailing(action: SettingsAction, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    when (action.style) {
        SettingsActionStyle.Toggle -> Switch(
            checked = action.checked,
            onCheckedChange = { onClick() },
            modifier = Modifier.semantics { contentDescription = action.label },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.surface,
                uncheckedTrackColor = p.border
            )
        )
        SettingsActionStyle.Choice -> {
            if (action.checked) {
                EchoIcon(EchoIconKind.Check, Modifier.size(18.dp), p.accent)
            } else {
                Spacer(Modifier.width(18.dp))
            }
        }
        SettingsActionStyle.Navigation,
        SettingsActionStyle.Default -> {
            if (action.value.isNotBlank()) {
                Text(
                    text = action.value,
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(16.dp), p.muted)
        }
        SettingsActionStyle.Destructive -> Unit
    }
}

@Composable
private fun SettingsOverviewCard(metrics: List<SettingsMetric>) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            metrics.forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (metric.compact) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        metric.label,
                        style = if (metric.compact) EchoTypography.caption else EchoTypography.bodyMedium,
                        color = p.muted,
                        modifier = Modifier.weight(1f),
                        maxLines = if (metric.compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        metric.value,
                        style = if (metric.compact) {
                            EchoTypography.caption
                        } else {
                            EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        },
                        color = p.text,
                        maxLines = if (metric.compact) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(if (metric.compact) 1.4f else 1f)
                            .padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

internal fun iconForSettingsAction(action: SettingsAction): EchoIconKind {
    val label = action.label
    fun has(vararg keys: String) = keys.any { label.contains(it, ignoreCase = true) }
    if (isBackAction(label)) return EchoIconKind.Back
    return when {
        has("Accent", "强调色") -> EchoIconKind.Swatch
        has("Appearance", "Theme", "外观", "主题") -> EchoIconKind.Palette
        has("Language", "语言") -> EchoIconKind.Language
        has("Advanced", "高级") -> EchoIconKind.Sparkle
        has("background", "背景") -> EchoIconKind.Palette
        has("audio effects", "equalizer", "eq", "bass", "virtualizer", "loudness", "音效", "均衡", "低音", "响度") -> EchoIconKind.Gauge
        has("speed", "播放速度", "速度") || label.endsWith("x") || label.endsWith("X") -> EchoIconKind.Gauge
        has("volume", "音量") || label.endsWith("%") -> EchoIconKind.Volume
        has("sleep", "timer", "睡眠", "定时") || label.endsWith("min") || label.contains("分钟") -> EchoIconKind.Timer
        has("lyrics", "offset", "歌词", "偏移") -> EchoIconKind.Lyrics
        has("quality", "lossless", "Hi-Res", "standard", "音质", "无损", "标准") -> EchoIconKind.Gauge
        has("gesture", "手势") -> EchoIconKind.More
        has("permission", "access", "权限", "授予", "访问") -> EchoIconKind.Permission
        has("About", "Version", "关于", "版本") -> EchoIconKind.Info
        has("Download", "下载") -> EchoIconKind.Download
        has("Restore", "恢复") -> EchoIconKind.Refresh
        has("Share", "Export", "分享", "导出") -> EchoIconKind.Upload
        has("clear", "delete", "cancel", "disable", "清空", "删除", "取消", "关闭") -> EchoIconKind.Delete
        has("scan", "reload", "sync", "扫描", "重新加载", "同步") -> EchoIconKind.Sync
        has("import", "导入") -> EchoIconKind.Import
        has("play", "播放") -> EchoIconKind.Play
        has("browse", "浏览") -> EchoIconKind.Collections
        has("manage", "edit", "管理", "编辑") -> EchoIconKind.Edit
        has("WebDAV") -> EchoIconKind.Folder
        has("remote", "network", "gateway", "provider", "endpoint", "source", "远程", "网络", "网关", "流媒体", "音源") ||
            label.trim().equals("Streaming", ignoreCase = true) || label.trim() == "串流" -> EchoIconKind.Network
        has("folder", "文件夹") -> EchoIconKind.Folder
        has("library", "song", "track", "曲库", "歌曲", "曲目") -> EchoIconKind.Library
        // Keep the plus only for actions that really create a new item.
        has("add", "new", "create", "添加", "新建", "创建") -> EchoIconKind.Action
        action.style == SettingsActionStyle.Destructive -> EchoIconKind.Delete
        action.style == SettingsActionStyle.Toggle || action.style == SettingsActionStyle.Choice -> EchoIconKind.Check
        else -> EchoIconKind.Settings
    }
}

private fun isBackAction(label: String): Boolean =
    label.startsWith("Back", ignoreCase = true) ||
        label.contains("\u8fd4\u56de") ||
        label.contains("返回")
