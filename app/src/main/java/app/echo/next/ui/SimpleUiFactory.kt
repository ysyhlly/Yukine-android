package app.echo.next.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object SimpleUiFactory {
    @JvmStatic
    fun messageBlock(context: Context, message: String): ComposeView =
        ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent {
                EchoTheme.EchoTheme { MessageBlock(message) }
            }
        }

    @JvmStatic
    fun sectionHeader(context: Context, title: String): ComposeView =
        ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent {
                EchoTheme.EchoTheme { SectionHeader(title) }
            }
        }

    @JvmStatic
    fun metricRow(context: Context, label: String, value: String): ComposeView =
        ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, context.dp(8)) }
            setContent {
                EchoTheme.EchoTheme { MetricRow(label, value) }
            }
        }

    @JvmStatic
    fun actionButton(context: Context, label: String, action: Runnable): ComposeView =
        ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, context.dp(50)
            ).also { it.setMargins(0, context.dp(8), 0, 0) }
            setContent {
                EchoTheme.EchoTheme { ActionButton(label, action) }
            }
        }
}

@Composable
private fun MessageBlock(message: String) {
    EchoEmptyCard(message)
}

@Composable
private fun SectionHeader(title: String) {
    EchoSectionTitle(title, Modifier.padding(top = 8.dp))
}

@Composable
private fun MetricRow(label: String, value: String) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(iconForText(label), Modifier.size(18.dp), p.accent)
            Spacer(Modifier.width(10.dp))
            Text(label,
                style = EchoTypography.bodyMedium,
                color = p.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(value,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, action: Runnable) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { action.run() },
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(iconForText(label), Modifier.size(24.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.muted)
        }
    }
}

private fun iconForText(text: String): EchoIconKind = when {
    text.contains("返回") -> EchoIconKind.Back
    text.contains("清空") || text.contains("删除") -> EchoIconKind.Delete
    text.contains("导入") -> EchoIconKind.Import
    text.contains("刷新") || text.contains("同步") -> EchoIconKind.Sync
    text.contains("新建") || text.contains("添加") || text.contains("授权") -> EchoIconKind.Action
    text.contains("播放") -> EchoIconKind.Play
    text.contains("收藏") -> EchoIconKind.Heart
    text.contains("列表") || text.contains("专辑") -> EchoIconKind.Collections
    text.contains("队列") -> EchoIconKind.Queue
    text.contains("文件夹") -> EchoIconKind.Folder
    text.contains("艺人") -> EchoIconKind.Artist
    text.contains("歌曲") -> EchoIconKind.Library
    text.contains("网络") || text.contains("远程") || text.contains("流媒体") -> EchoIconKind.Network
    text.contains("主题") || text.contains("强调色") -> EchoIconKind.Palette
    text.contains("音量") -> EchoIconKind.Volume
    text.contains("设置") -> EchoIconKind.Settings
    text.contains("管理") -> EchoIconKind.Edit
    text.contains("导出") -> EchoIconKind.Import
    else -> EchoIconKind.Action
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()
