package app.echo.next.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.next.MainActivityNetworkSourcesUiState
import kotlinx.coroutines.flow.StateFlow

data class NetworkSourceUiState(
    val id: Long, val title: String, val subtitle: String, val status: String
)

data class NetworkSourceActions(
    val onTest: Runnable, val onSync: Runnable, val onPlay: Runnable,
    val onTracks: Runnable, val onEdit: Runnable, val onDelete: Runnable
)

data class NetworkSourceLabels(
    val testLabel: String = "Test",
    val syncLabel: String = "Sync",
    val playLabel: String = "Play",
    val tracksLabel: String = "Tracks",
    val editLabel: String = "Edit",
    val deleteLabel: String = "Delete"
)

object NetworkSourcesScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityNetworkSourcesUiState>,
        actions: List<NetworkSourceActions>
    ): ComposeView = create(context, state, actions, emptyList(), "")

    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityNetworkSourcesUiState>,
        actions: List<NetworkSourceActions>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String
    ): ComposeView = create(context, state, actions, headerActions, emptyText, NetworkSourceLabels())

    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityNetworkSourcesUiState>,
        actions: List<NetworkSourceActions>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        labels: NetworkSourceLabels
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState = state.collectAsState()
                NetworkSourcesScreen(uiState.value.title, uiState.value.rows, actions, headerActions, emptyText, labels)
            }
        }
    }
}

@Composable
private fun NetworkSourcesScreen(
    title: String,
    sources: List<NetworkSourceUiState>,
    actions: List<NetworkSourceActions>,
    headerActions: List<TrackListHeaderAction>,
    emptyText: String,
    labels: NetworkSourceLabels
) {
    val p = EchoTheme.colors()
    val titleBackAction = headerActions.firstOrNull { isBackAction(it.label) }
    val visibleHeaderActions = if (titleBackAction != null) headerActions.drop(1) else headerActions
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
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
            items = visibleHeaderActions,
            key = { index, action -> "header:${action.label}:$index" }
        ) { _, action ->
            SourceHeaderAction(action)
        }
        itemsIndexed(
            items = sources,
            key = { _, source -> source.id }
        ) { i, source ->
            actions.getOrNull(i)?.let { action ->
                SourceCard(source, action, labels)
            }
        }
        if (sources.isEmpty() && emptyText.isNotBlank()) {
            item(key = "empty") {
                SourceMessage(emptyText)
            }
        }
    }
}

@Composable
private fun SourceHeaderAction(action: TrackListHeaderAction) {
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
            EchoIcon(EchoIconKind.Back, Modifier.size(22.dp), p.accent)
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

@Composable
private fun SourceMessage(message: String) {
    EchoEmptyCard(message)
}

private fun isBackAction(label: String): Boolean =
    label.contains("Back", ignoreCase = true) || label.contains("\u8fd4\u56de")

@Composable
private fun SourceCard(source: NetworkSourceUiState, actions: NetworkSourceActions, labels: NetworkSourceLabels) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier.echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                source.title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                source.subtitle,
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (source.status.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.echoGlassLayer(p, EchoShapes.small),
                    shape = EchoShapes.small,
                    color = Color.Transparent
                ) {
                    Text(
                        source.status,
                        style = EchoTypography.small,
                        color = p.muted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SrcBtn(EchoIconKind.Action, labels.testLabel, Modifier.weight(1f)) { actions.onTest.run() }
                SrcBtn(EchoIconKind.Sync, labels.syncLabel, Modifier.weight(1f)) { actions.onSync.run() }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SrcBtn(EchoIconKind.Play, labels.playLabel, Modifier.weight(1f)) { actions.onPlay.run() }
                SrcBtn(EchoIconKind.Library, labels.tracksLabel, Modifier.weight(1f)) { actions.onTracks.run() }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SrcBtn(EchoIconKind.Edit, labels.editLabel, Modifier.weight(1f)) { actions.onEdit.run() }
                SrcBtn(EchoIconKind.Delete, labels.deleteLabel, Modifier.weight(1f)) { actions.onDelete.run() }
            }
        }
    }
}

@Composable
private fun SrcBtn(icon: EchoIconKind, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .echoGlassLayer(p, EchoShapes.small)
            .semantics { contentDescription = label },
        shape = EchoShapes.small,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            EchoIcon(icon, Modifier.size(16.dp), p.muted)
            Spacer(Modifier.width(5.dp))
            Text(label, style = EchoTypography.small, color = p.muted, maxLines = 1)
        }
    }
}
