package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                StateScreen(message, actions)
            }
        }
    }
}

@Composable
private fun StateScreen(message: String, actions: List<StateScreenAction>) {
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "message") {
            StateMessage(message)
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
private fun StateMessage(message: String) {
    EchoEmptyCard(message)
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
    label.contains("Grant") -> EchoIconKind.Action
    label.contains("Back") -> EchoIconKind.Back
    label.contains("Play") -> EchoIconKind.Play
    else -> EchoIconKind.Action
}
