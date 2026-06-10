package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class StateScreenAction(val label: String, val onClick: Runnable)

object StateScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        message: String,
        actions: List<StateScreenAction>
    ): ComposeView = create(context, message, "", actions)

    @JvmStatic
    fun create(
        context: Context,
        title: String,
        description: String,
        actions: List<StateScreenAction>
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                StateScreen(title, description, actions)
            }
        }
    }
}

@Composable
private fun StateScreen(title: String, description: String, actions: List<StateScreenAction>) {
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "message") {
            EchoStateCard(title, description)
        }
        itemsIndexed(
            items = actions,
            key = { index, action -> "action:${action.label}:$index" }
        ) { _, action ->
            StateAction(action)
        }
    }
}

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

@Composable
private fun StateAction(action: StateScreenAction) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { action.onClick.run() },
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = action.label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(iconForStateAction(action.label), Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                action.label,
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

private fun iconForStateAction(label: String): EchoIconKind = when {
    label.contains("Grant", ignoreCase = true) || label.contains("授权") -> EchoIconKind.Action
    label.contains("Import", ignoreCase = true) || label.contains("导入") -> EchoIconKind.Import
    label.contains("Scan", ignoreCase = true) || label.contains("扫描") -> EchoIconKind.Refresh
    label.contains("Back", ignoreCase = true) || label.contains("返回") -> EchoIconKind.Back
    label.contains("Play", ignoreCase = true) || label.contains("播放") -> EchoIconKind.Play
    else -> EchoIconKind.Action
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
