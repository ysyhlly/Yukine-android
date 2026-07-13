package app.yukine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yukine.streaming.StreamingTrack

fun interface StreamingTrackAction {
    fun run(track: StreamingTrack)
}

data class StreamingUsageNoticeLabels(
    val title: String,
    val body: String
) {
    companion object {
        @JvmStatic
        fun defaults(): StreamingUsageNoticeLabels = StreamingUsageNoticeLabels(
            title = "流媒体与账号说明",
            body = "本应用仅用于个人学习与本地音乐管理。流媒体搜索、播放、下载、歌词和封面可能受版权与平台协议约束。"
        )
    }
}

@Composable
fun StreamingUsageNotice(labels: StreamingUsageNoticeLabels, modifier: Modifier = Modifier) {
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(EchoIconKind.Network, Modifier.size(16.dp), EchoTheme.colors().accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    labels.title,
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = EchoTheme.colors().heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                labels.body,
                style = EchoTypography.caption,
                color = EchoTheme.colors().muted
            )
        }
    }
}
