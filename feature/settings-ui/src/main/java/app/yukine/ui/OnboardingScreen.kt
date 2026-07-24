package app.yukine.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class OnboardingActions(
    val requestPermissions: Runnable,
    val addMusicFolder: Runnable,
    val scanLibrary: Runnable,
    val importPlaylist: Runnable,
    val openStreaming: Runnable,
    val finish: Runnable
)

data class OnboardingLabels(
    val title: String,
    val subtitle: String,
    val summaryTitle: String,
    val summaryEmpty: String,
    val summaryFolders: String,
    val folderTitle: String,
    val folderDescription: String,
    val folderConfigured: String,
    val folderAction: String,
    val deviceTitle: String,
    val deviceDescription: String,
    val devicePermissionDescription: String,
    val deviceScanning: String,
    val deviceCompleted: String,
    val deviceAction: String,
    val deviceRescanAction: String,
    val playlistAction: String,
    val streamingAction: String,
    val optionalHint: String,
    val finishHint: String,
    val finishAction: String
) {
    companion object {
        fun defaults() = OnboardingLabels(
            title = "Welcome to Yukine",
            subtitle = "Choose where your music comes from, or set it up later.",
            summaryTitle = "Music sources",
            summaryEmpty = "No source selected yet",
            summaryFolders = "Selected folders: %d",
            folderTitle = "Add a music folder",
            folderDescription = "Choose only the folders Yukine should read. No full-device permission is needed.",
            folderConfigured = "%d folder(s) configured. You can add more at any time.",
            folderAction = "Choose folder",
            deviceTitle = "Scan all music on this device",
            deviceDescription = "Optional. Yukine will ask for audio access only when you start this scan.",
            devicePermissionDescription = "Audio access will be requested for this optional scan.",
            deviceScanning = "Scanning in the background. You can enter the app now.",
            deviceCompleted = "Device scan complete. You can rescan later from Settings.",
            deviceAction = "Scan device",
            deviceRescanAction = "Rescan",
            playlistAction = "Import playlist",
            streamingAction = "Connect streaming",
            optionalHint = "Optional",
            finishHint = "You can change every source later in Settings.",
            finishAction = "Enter Yukine"
        )
    }
}

@Composable
fun OnboardingScreen(
    audioPermissionGranted: Boolean,
    libraryScanCompleted: Boolean = false,
    libraryScanInProgress: Boolean = false,
    localMusicFolderCount: Int = 0,
    labels: OnboardingLabels = OnboardingLabels.defaults(),
    noticeLabels: StreamingUsageNoticeLabels = StreamingUsageNoticeLabels.defaults(),
    actions: OnboardingActions
) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .echoPageBackground()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EchoPageTitle(labels.title, subtitle = labels.subtitle)
                SourceSummary(localMusicFolderCount, labels)
                SourceChoiceCard(
                    icon = EchoIconKind.Folder,
                    title = labels.folderTitle,
                    description = if (localMusicFolderCount > 0) {
                        labels.folderConfigured.format(localMusicFolderCount)
                    } else {
                        labels.folderDescription
                    },
                    actionLabel = labels.folderAction,
                    primary = true,
                    onClick = actions.addMusicFolder
                )
                SourceChoiceCard(
                    icon = EchoIconKind.Library,
                    title = labels.deviceTitle,
                    description = when {
                        libraryScanInProgress -> labels.deviceScanning
                        libraryScanCompleted -> labels.deviceCompleted
                        audioPermissionGranted -> labels.deviceDescription
                        else -> labels.devicePermissionDescription
                    },
                    actionLabel = if (libraryScanCompleted) labels.deviceRescanAction else labels.deviceAction,
                    primary = false,
                    enabled = !libraryScanInProgress,
                    progress = libraryScanInProgress,
                    onClick = actions.scanLibrary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OptionalAction(
                        label = labels.playlistAction,
                        icon = EchoIconKind.PlaylistAdd,
                        modifier = Modifier.weight(1f),
                        onClick = actions.importPlaylist
                    )
                    OptionalAction(
                        label = labels.streamingAction,
                        icon = EchoIconKind.Network,
                        modifier = Modifier.weight(1f),
                        onClick = actions.openStreaming
                    )
                }
                StreamingUsageNotice(noticeLabels)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(labels.finishHint, style = EchoTypography.caption, color = p.muted)
                Surface(
                    onClick = { actions.finish.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = labels.finishAction },
                    shape = EchoShapes.medium,
                    color = p.accent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoIcon(EchoIconKind.Play, Modifier.size(18.dp), p.onAccent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            labels.finishAction,
                            style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = p.onAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSummary(folderCount: Int, labels: OnboardingLabels) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(EchoShapes.small)
                    .background(p.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                EchoIcon(EchoIconKind.Mark, Modifier.size(22.dp), p.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    labels.summaryTitle,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading
                )
                Text(
                    if (folderCount > 0) labels.summaryFolders.format(folderCount) else labels.summaryEmpty,
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (folderCount > 0) p.accent else p.muted)
            )
        }
    }
}

@Composable
private fun SourceChoiceCard(
    icon: EchoIconKind,
    title: String,
    description: String,
    actionLabel: String,
    primary: Boolean,
    enabled: Boolean = true,
    progress: Boolean = false,
    onClick: Runnable
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { onClick.run() },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = title },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(EchoShapes.small)
                    .background(if (primary) p.accent.copy(alpha = 0.22f) else p.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                EchoIcon(icon, Modifier.size(23.dp), if (primary) p.accent else p.muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (progress) "…" else actionLabel,
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (primary) p.accent else p.text,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OptionalAction(
    label: String,
    icon: EchoIconKind,
    modifier: Modifier,
    onClick: Runnable
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { onClick.run() },
        modifier = modifier.height(44.dp),
        shape = EchoShapes.medium,
        color = p.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(17.dp), p.muted)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
