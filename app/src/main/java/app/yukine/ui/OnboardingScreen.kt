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
import androidx.compose.ui.unit.sp

data class OnboardingActions(
    val requestPermissions: Runnable,
    val scanLibrary: Runnable,
    val importPlaylist: Runnable,
    val openStreaming: Runnable,
    val finish: Runnable
)

@Composable
fun OnboardingScreen(
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    libraryScanCompleted: Boolean = false,
    libraryScanInProgress: Boolean = false,
    actions: OnboardingActions
) {
    val p = EchoTheme.colors()
    val permissionsReady = audioPermissionGranted && notificationPermissionGranted
    val setupComplete = permissionsReady && libraryScanCompleted
    val missingSetupText = missingSetupText(
        audioPermissionGranted = audioPermissionGranted,
        notificationPermissionGranted = notificationPermissionGranted,
        libraryScanCompleted = libraryScanCompleted,
        libraryScanInProgress = libraryScanInProgress
    )
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
                EchoPageTitle(
                    "Yukine",
                    subtitle = "按下面 4 步完成准备，完成前不会进入首页。"
                )
                StartupSummary(
                    permissionsReady = permissionsReady,
                    audioPermissionGranted = audioPermissionGranted,
                    notificationPermissionGranted = notificationPermissionGranted,
                    libraryScanCompleted = libraryScanCompleted
                )
                OnboardingStep(
                    number = "01",
                    icon = EchoIconKind.Action,
                    title = "第 1 步：允许访问音乐和通知",
                    status = if (permissionsReady) {
                        "已完成。Yukine 可以读取音乐，也能在通知栏显示播放控制。"
                    } else {
                        "先点这里授权。没有这些权限，Yukine 还不能帮你找歌和保持播放控制。"
                    },
                    actionLabel = if (permissionsReady) "已完成" else "去授权",
                    active = !permissionsReady,
                    onClick = actions.requestPermissions
                )
                OnboardingStep(
                    number = "02",
                    icon = EchoIconKind.Library,
                    title = "第 2 步：扫描手机里的歌曲",
                    status = when {
                        libraryScanCompleted -> "已完成。现在可以进入首页，也可以以后再重新扫描。"
                        libraryScanInProgress -> "正在找歌，请稍等一下。完成后按钮会自动解锁。"
                        audioPermissionGranted -> "点开始扫描，Yukine 会自动把手机里的音乐整理出来。"
                        else -> "先完成第 1 步，再回来点开始扫描。"
                    },
                    actionLabel = if (libraryScanCompleted) "重扫" else if (libraryScanInProgress) "请稍等" else "开始扫描",
                    active = audioPermissionGranted && !libraryScanCompleted,
                    enabled = audioPermissionGranted && !libraryScanInProgress,
                    onClick = actions.scanLibrary
                )
                OnboardingStep(
                    number = "03",
                    icon = EchoIconKind.PlaylistAdd,
                    title = "第 3 步：导入已有歌单",
                    status = if (setupComplete) {
                        "可选。如果你有 M3U 或 M3U8 歌单文件，点这里导入；没有也没关系。"
                    } else {
                        "先完成前两步。进入前可以顺手导入已有歌单。"
                    },
                    actionLabel = if (setupComplete) "导入歌单" else "稍后",
                    active = false,
                    enabled = setupComplete,
                    onClick = actions.importPlaylist
                )
                OnboardingStep(
                    number = "04",
                    icon = EchoIconKind.Network,
                    title = "第 4 步：流媒体稍后再连",
                    status = if (setupComplete) {
                        "这是可选项。想同步网易云歌单时，再来这里连接账号。"
                    } else {
                        "先不用管这里。完成前两步后，你再决定要不要连接。"
                    },
                    actionLabel = if (setupComplete) "去连接" else "稍后",
                    active = false,
                    enabled = setupComplete,
                    onClick = actions.openStreaming
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (setupComplete) {
                        "都准备好了。点下面按钮进入首页，开始播放你的音乐。"
                    } else {
                        missingSetupText
                    },
                    style = EchoTypography.caption,
                    color = if (setupComplete) p.muted else p.accent
                )
                Surface(
                    onClick = { actions.finish.run() },
                    enabled = setupComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics {
                            contentDescription = if (setupComplete) "进入 Yukine" else "完成设置后才能进入 Yukine"
                        },
                    shape = EchoShapes.medium,
                    color = if (setupComplete) p.accent else p.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoIcon(
                            if (setupComplete) EchoIconKind.Play else EchoIconKind.Check,
                            Modifier.size(18.dp),
                            if (setupComplete) p.onAccent else p.muted
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (setupComplete) "进入 Yukine" else "请先完成设置",
                            style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (setupComplete) p.onAccent else p.muted,
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
private fun StartupSummary(
    permissionsReady: Boolean,
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    libraryScanCompleted: Boolean
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        "启动检查",
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.heading
                    )
                    Text(
                        if (permissionsReady && libraryScanCompleted) {
                            "已经准备好，可以进入首页"
                        } else {
                            "从第 1 步开始，按顺序完成"
                        },
                        style = EchoTypography.caption,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("音频", audioPermissionGranted, Modifier.weight(1f))
                    StatusPill("通知", notificationPermissionGranted, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("曲库", libraryScanCompleted, Modifier.weight(1f))
                    StatusPill("流媒体", false, Modifier.weight(1f), optional = true)
                }
            }
        }
    }
}

private fun missingSetupText(
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    libraryScanCompleted: Boolean,
    libraryScanInProgress: Boolean
): String {
    val missing = ArrayList<String>()
    if (!audioPermissionGranted || !notificationPermissionGranted) {
        missing.add("第 1 步授权")
    }
    if (!libraryScanCompleted) {
        missing.add(if (libraryScanInProgress) "等待扫描完成" else "第 2 步扫描歌曲")
    }
    return "还不能进入。请先完成：${missing.joinToString("、")}"
}

@Composable
private fun StatusPill(
    label: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
    optional: Boolean = false
) {
    val p = EchoTheme.colors()
    val color = if (ready) p.accent else p.muted
    Row(
        modifier = modifier
            .clip(EchoShapes.small)
            .background(p.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ready) "$label 已开" else if (optional) "$label 可选" else "$label 待开",
            style = EchoTypography.caption.copy(fontSize = 11.sp),
            color = p.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OnboardingStep(
    number: String,
    icon: EchoIconKind,
    title: String,
    status: String,
    actionLabel: String,
    active: Boolean,
    enabled: Boolean = true,
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
                    .size(42.dp)
                    .clip(EchoShapes.small)
                    .background((if (active) p.accent else p.surfaceVariant).copy(alpha = if (active) 0.22f else 1f)),
                contentAlignment = Alignment.Center
            ) {
                EchoIcon(icon, Modifier.size(22.dp), if (active) p.accent else p.muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        number,
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.Bold),
                        color = if (active) p.accent else p.muted
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    status,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.height(34.dp),
                shape = EchoShapes.small,
                color = if (active) p.accent else p.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        actionLabel,
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) p.onAccent else p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    EchoIcon(EchoIconKind.ChevronRight, Modifier.size(14.dp), if (active) p.onAccent else p.muted)
                }
            }
        }
    }
}
