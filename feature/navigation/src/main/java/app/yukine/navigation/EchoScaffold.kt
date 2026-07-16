package app.yukine.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.yukine.BackgroundTransform
import app.yukine.ui.EchoGlassSurface
import app.yukine.ui.EchoBackgroundBlurDefaults
import app.yukine.ui.EchoGlassDefaults
import app.yukine.ui.EchoIcon
import app.yukine.ui.EchoIconKind
import app.yukine.ui.EchoMobileLayoutMetrics
import app.yukine.ui.EchoMotion
import app.yukine.ui.EchoPageBackground
import app.yukine.ui.EchoShapes
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoTypography
import app.yukine.ui.LocalEchoGlassEnabled
import app.yukine.ui.LocalEchoGlassBlurRadius
import app.yukine.ui.LocalEchoGlassOpacity
import app.yukine.ui.EchoCompositeBackdrop
import app.yukine.ui.LocalEchoCompositeBackdrop
import app.yukine.ui.LocalEchoNowBarCompactProgress
import app.yukine.ui.LocalEchoNowBarScrollProgress
import app.yukine.ui.LocalEchoNowBarPageScrollEvent
import app.yukine.ui.LocalEchoNowBarBottomInset
import app.yukine.ui.LocalEchoNowBarTopCloudClearanceChanged
import app.yukine.ui.LocalEchoPageBottomChromeInset
import app.yukine.ui.blockPointerInputBehind
import app.yukine.ui.echoPressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A single bottom-navigation entry: the tab it selects plus its localized label.
 */
data class EchoTabItem(
    val tab: TabRoute,
    val label: String
)

private enum class ChromeScrollMotion {
    Idle,
    CompactDown,
    StretchApart
}

/**
 * Single-Activity Compose scaffold for Yukine.
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
    backgroundUri: String = "",
    backgroundTransform: BackgroundTransform = BackgroundTransform.IDENTITY,
    customBackgroundVisible: Boolean = true,
    customBackgroundBlurEnabled: Boolean = false,
    customBackgroundBlurRadiusDp: Float = EchoBackgroundBlurDefaults.DEFAULT_RADIUS_DP,
    glassBlurEnabled: Boolean = false,
    glassBlurRadiusDp: Float = EchoGlassDefaults.BLUR_RADIUS_DP,
    glassSurfaceOpacity: Float = EchoGlassDefaults.SURFACE_OPACITY,
    content: @Composable (Modifier) -> Unit
) {
    var chromeScrollMotion by remember { mutableStateOf(ChromeScrollMotion.Idle) }
    var pageScrollEvent by remember { mutableStateOf(0) }
    var chromeRestoreJob by remember { mutableStateOf<Job?>(null) }
    val chromeScope = rememberCoroutineScope()
    val chromeScrollConnection = remember(chromeScope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (abs(available.y) < 0.5f) return Offset.Zero
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val nextDirection = if (available.y > 0f) 1 else -1
                    val currentDirection = when {
                        pageScrollEvent > 0 -> 1
                        pageScrollEvent < 0 -> -1
                        else -> 0
                    }
                    if (chromeScrollMotion == ChromeScrollMotion.Idle ||
                        nextDirection != currentDirection
                    ) {
                        val nextSequence = abs(pageScrollEvent) + 1
                        pageScrollEvent = if (nextDirection > 0) nextSequence else -nextSequence
                    }
                }
                chromeScrollMotion = if (available.y < 0f) {
                    ChromeScrollMotion.StretchApart
                } else {
                    ChromeScrollMotion.CompactDown
                }
                chromeRestoreJob?.cancel()
                chromeRestoreJob = chromeScope.launch {
                    delay(EchoMobileLayoutMetrics.nowBarScrollRestoreDelayMs)
                    chromeScrollMotion = ChromeScrollMotion.Idle
                }
                return Offset.Zero
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { chromeRestoreJob?.cancel() }
    }
    val chromeScrollProgress by animateFloatAsState(
        targetValue = when (chromeScrollMotion) {
            ChromeScrollMotion.Idle -> 0f
            ChromeScrollMotion.CompactDown -> 1f
            ChromeScrollMotion.StretchApart -> -1f
        },
        animationSpec = EchoMotion.floatSpring(),
        label = "floatingChromeScrollMotion"
    )
    val chromeCompactProgress = chromeScrollProgress.coerceIn(0f, 1f)
    val chromeStretchProgress = (-chromeScrollProgress).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val contentLayer = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val compositeBackdrop = remember(contentLayer) {
        EchoCompositeBackdrop(contentLayer) { contentOrigin }
    }
    var requestedTopCloudClearance by remember { mutableStateOf(0.dp) }
    var bottomNavHeightPx by remember { mutableStateOf(0) }
    val bottomNavHeight = with(density) { bottomNavHeightPx.toDp() }
    val topCloudClearance by animateDpAsState(
        targetValue = requestedTopCloudClearance,
        animationSpec = tween(EchoMobileLayoutMetrics.nowBarDockSizeDurationMs),
        label = "topCloudContentClearance"
    )
    EchoPageBackground(
        backgroundUri = backgroundUri,
        modifier = Modifier.fillMaxSize(),
        transform = backgroundTransform,
        customBackgroundVisible = customBackgroundVisible,
        customBackgroundBlurEnabled = customBackgroundBlurEnabled,
        customBackgroundBlurRadiusDp = customBackgroundBlurRadiusDp
    ) {
        CompositionLocalProvider(
            LocalEchoGlassEnabled provides glassBlurEnabled,
            LocalEchoGlassBlurRadius provides glassBlurRadiusDp,
            LocalEchoGlassOpacity provides EchoGlassDefaults.normalizeSurfaceOpacity(glassSurfaceOpacity)
        ) {
          Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (glassBlurEnabled) {
                            Modifier
                                .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                                .drawWithContent {
                                    contentLayer.renderEffect = null
                                    contentLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(contentLayer)
                                }
                        } else {
                            Modifier
                        }
                    )
            ) {
              Column(
                  modifier = Modifier
                      .fillMaxSize()
                      .windowInsetsPadding(WindowInsets.statusBars)
              ) {
                topBar()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(
                        LocalEchoPageBottomChromeInset provides
                            bottomNavHeight + EchoMobileLayoutMetrics.nowBarHeight
                    ) {
                        content(
                            Modifier
                                .fillMaxSize()
                                .padding(top = topCloudClearance)
                                .nestedScroll(chromeScrollConnection)
                        )
                    }
                }
              }
            }
            CompositionLocalProvider(
                LocalEchoCompositeBackdrop provides if (glassBlurEnabled) compositeBackdrop else null
            ) {
              Box(
                  modifier = Modifier
                      .fillMaxSize()
                      .zIndex(1f)
              ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { bottomNavHeightPx = it.size.height }
                        .graphicsLayer {
                            translationY = with(density) {
                                -EchoMobileLayoutMetrics.bottomTabScrollTranslation.toPx() * chromeCompactProgress -
                                    EchoMobileLayoutMetrics.bottomTabScrollStretchTranslation.toPx() * chromeStretchProgress
                            }
                            scaleY = 1f -
                                (1f - EchoMobileLayoutMetrics.bottomTabScrollScale) * chromeCompactProgress +
                                (EchoMobileLayoutMetrics.bottomTabScrollStretchScale - 1f) * chromeStretchProgress
                            transformOrigin = TransformOrigin.Center
                        }
                ) {
                    EchoBottomNav(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected
                    )
                }
                CompositionLocalProvider(
                    LocalEchoNowBarCompactProgress provides chromeCompactProgress,
                    LocalEchoNowBarScrollProgress provides chromeScrollProgress,
                    LocalEchoNowBarPageScrollEvent provides pageScrollEvent,
                    LocalEchoNowBarBottomInset provides bottomNavHeight,
                    LocalEchoNowBarTopCloudClearanceChanged provides { clearance ->
                        requestedTopCloudClearance = clearance
                    }
                ) {
                    nowBar()
                }
              }
            }
          }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = EchoMobileLayoutMetrics.floatingChromeHorizontalPadding,
                top = EchoMobileLayoutMetrics.floatingChromeGap,
                end = EchoMobileLayoutMetrics.floatingChromeHorizontalPadding,
                bottom = EchoMobileLayoutMetrics.floatingChromeBottomPadding
            )
    ) {
        EchoGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("echo-bottom-nav-surface")
                .blockPointerInputBehind(),
            shape = EchoShapes.pill,
            elevation = EchoMobileLayoutMetrics.floatingChromeElevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
