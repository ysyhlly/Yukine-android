package app.yukine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EchoStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: EchoIconKind = iconForStateMessage(title, description)
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .echoGlassLayer(p, EchoShapes.medium),
                contentAlignment = Alignment.Center
            ) {
                EchoIcon(icon, Modifier.size(24.dp), p.accent)
            }
            Text(
                title,
                style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = EchoTypography.body,
                    color = p.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun iconForStateMessage(title: String, description: String): EchoIconKind {
    val text = "$title $description"
    return when {
        text.contains("permission", ignoreCase = true) || text.contains("权限") -> EchoIconKind.Action
        text.contains("music", ignoreCase = true) || text.contains("音乐") -> EchoIconKind.Library
        text.contains("lyrics", ignoreCase = true) || text.contains("歌词") -> EchoIconKind.Lyrics
        text.contains("queue", ignoreCase = true) || text.contains("队列") -> EchoIconKind.Queue
        text.contains("network", ignoreCase = true) || text.contains("source", ignoreCase = true) ||
            text.contains("网络") || text.contains("来源") -> EchoIconKind.Network
        text.contains("playback", ignoreCase = true) || text.contains("播放") -> EchoIconKind.Play
        else -> EchoIconKind.Sparkle
    }
}
