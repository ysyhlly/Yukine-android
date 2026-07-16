package app.yukine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LibrarySyncControls(
    state: LibraryUiState,
    actionHandler: LibraryActionHandler
) {
    val palette = EchoTheme.colors()
    Surface(
        onClick = { actionHandler.onAction(LibraryAction.SyncLibrary) },
        enabled = !state.operationInProgress,
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(palette, EchoShapes.medium)
            .semantics { contentDescription = state.labels.syncLibrary },
        shape = EchoShapes.medium,
        color = palette.surface.copy(alpha = 0.78f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.operationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = palette.accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    EchoIcon(EchoIconKind.Sync, Modifier.size(22.dp), palette.accent)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (state.operationInProgress) {
                        state.labels.syncingLibrary
                    } else {
                        state.labels.syncLibrary
                    },
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.text
                )
                Text(
                    text = state.labels.syncLibraryDescription,
                    style = EchoTypography.caption,
                    color = palette.muted
                )
                Text(
                    text = state.labels.autoSync,
                    style = EchoTypography.small,
                    color = if (state.autoSyncEnabled) palette.accent else palette.muted
                )
            }
            Switch(
                checked = state.autoSyncEnabled,
                onCheckedChange = {
                    actionHandler.onAction(LibraryAction.SetAutoSyncEnabled(it))
                },
                modifier = Modifier.semantics {
                    contentDescription = state.labels.autoSync
                }
            )
        }
    }
}
