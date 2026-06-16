package app.echo.next.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.next.ui.EchoGlassSurface
import app.echo.next.ui.EchoIcon
import app.echo.next.ui.EchoIconKind
import app.echo.next.ui.EchoMobileLayoutMetrics
import app.echo.next.ui.EchoMotion
import app.echo.next.ui.EchoShapes
import app.echo.next.ui.EchoTheme
import app.echo.next.ui.EchoTypography
import app.echo.next.ui.echoPageBackground
import app.echo.next.ui.echoPressScale

/**
 * A single bottom-navigation entry: the tab it selects plus its localized label.
 */
data class EchoTabItem(
    val tab: TabRoute,
    val label: String
)

/**
 * Single-Activity Compose scaffold for ECHO NEXT.
 *
 * Owns the persistent chrome that survives across tab switches:
 *  - [topBar]: persistent header/search chrome slot, fed by the host (empty by default),
 *  - [content]: the active destination (a migrated native Compose screen, or the legacy
 *    View shell for tabs not yet migrated),
 *  - [nowBar]: the persistent now-playing bar slot, fed by the host,
 *  - the bottom navigation row.
 *
 * The scaffold itself holds no business state — [selectedTab] and [onTabSelected] are driven
 * by the NavHost so back-stack and route remain the single source of truth.
 */
@Composable
fun EchoScaffold(
    tabs: List<EchoTabItem>,
    selectedTab: TabRoute,
    onTabSelected: (TabRoute) -> Unit,
    nowBar: @Composable () -> Unit,
    topBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    Box(modifier = Modifier.echoPageBackground()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            topBar()
            content(Modifier.weight(1f).fillMaxWidth())
            nowBar()
            EchoBottomNav(tabs, selectedTab, onTabSelected)
        }
    }
}

@Composable
private fun EchoBottomNav(
    tabs: List<EchoTabItem>,
    selectedTab: TabRoute,
    onTabSelected: (TabRoute) -> Unit
) {
    if (tabs.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEach { item ->
            EchoBottomNavItem(
                item = item,
                selected = item.tab.route == selectedTab.route,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(item.tab) }
            )
        }
    }
}

@Composable
private fun EchoBottomNavItem(
    item: EchoTabItem,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = EchoMotion.floatSpring(),
        label = "bottomNavIconScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .echoPressScale(interaction)
            .semantics { contentDescription = item.label },
        shape = EchoShapes.full,
        color = if (selected) p.accentSoft else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = EchoMobileLayoutMetrics.bottomTabVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            EchoIcon(
                kind = iconForTab(item.tab),
                modifier = Modifier
                    .size(EchoMobileLayoutMetrics.bottomTabIconSize)
                    .scale(iconScale),
                color = if (selected) p.accent else p.muted
            )
            Text(
                item.label,
                style = EchoTypography.small.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (selected) p.accent else p.muted,
                maxLines = 1
            )
        }
    }
}

private fun iconForTab(tab: TabRoute): EchoIconKind = when (tab) {
    HomeTab -> EchoIconKind.Mark
    LibraryTab -> EchoIconKind.Library
    QueueTab -> EchoIconKind.Queue
    SettingsTab -> EchoIconKind.Settings
    else -> EchoIconKind.Mark
}
