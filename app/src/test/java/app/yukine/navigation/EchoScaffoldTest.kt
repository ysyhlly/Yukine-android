package app.yukine.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoPageBackground
import app.yukine.ui.LocalEchoCustomBackground
import app.yukine.ui.NowBar
import app.yukine.ui.SeekAction
import app.yukine.ui.nowBarEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed Compose UI test for [EchoScaffold].
 *
 * This is the verification spine for the single-Activity migration: it runs the real Compose
 * tree on the JVM (no emulator), so the new shell chrome can be regression-tested as part of
 * `testDebugUnitTest`. Every tab the migration cuts over must keep these contracts green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EchoScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(
        EchoTabItem(HomeTab, "首页"),
        EchoTabItem(LibraryTab, "曲库"),
        EchoTabItem(CollectionsTab, "收藏"),
        EchoTabItem(SettingsTab, "设置")
    )

    @Test
    fun scaffold_rendersAllBottomNavLabels() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {}
                ) { Text("content") }
            }
        }

        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("曲库").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun scaffold_rendersContentSlot() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {}
                ) { Text("当前内容") }
            }
        }

        composeRule.onNodeWithText("当前内容").assertIsDisplayed()
    }

    @Test
    fun scaffold_rendersNowBarSlot() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = { Text("now-bar-slot") }
                ) { Text("content") }
            }
        }

        composeRule.onNodeWithText("now-bar-slot").assertIsDisplayed()
    }

    @Test
    fun scaffold_rendersFloatingChromeWithGaussianBackdropBlur() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = { Text("blurred-now-bar") },
                    glassBlurEnabled = true,
                    glassBlurRadiusDp = 24f
                ) { contentModifier ->
                    Box(contentModifier) {
                        Text("blurred-content")
                    }
                }
            }
        }

        composeRule.onNodeWithText("blurred-content").assertIsDisplayed()
        composeRule.onNodeWithText("blurred-now-bar").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun customBackgroundBlurUsesItsOwnBackgroundLayer() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {},
                    backgroundUri = "content://background/blur-test",
                    customBackgroundBlurEnabled = true,
                    customBackgroundBlurRadiusDp = 32f,
                    glassBlurEnabled = false
                ) { Text("background-blur-content") }
            }
        }

        composeRule.onNodeWithTag("custom-background-blur-layer").assertIsDisplayed()
        composeRule.onNodeWithText("background-blur-content").assertIsDisplayed()
    }

    @Test
    fun upwardScrollStretchesChromeAndTightensGap() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = { Text("motion-now-bar") }
                ) { contentModifier ->
                    Column(
                        modifier = contentModifier.verticalScroll(rememberScrollState())
                    ) {
                        Text("scroll-content")
                        Spacer(Modifier.height(1200.dp))
                    }
                }
            }
        }

        val initialNowBar = composeRule.onNodeWithText("motion-now-bar")
            .fetchSemanticsNode().boundsInRoot
        val initialTab = composeRule.onNodeWithText("首页")
            .fetchSemanticsNode().boundsInRoot
        val initialGap = initialTab.top - initialNowBar.bottom

        composeRule.onNodeWithText("scroll-content").performTouchInput { swipeUp(durationMillis = 120L) }
        composeRule.mainClock.advanceTimeBy(80L)

        val stretchedNowBar = composeRule.onNodeWithText("motion-now-bar")
            .fetchSemanticsNode().boundsInRoot
        val stretchedTab = composeRule.onNodeWithText("首页")
            .fetchSemanticsNode().boundsInRoot
        val stretchedGap = stretchedTab.top - stretchedNowBar.bottom
        assertTrue(stretchedGap < initialGap)

        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun pageScrollCollapsesTopCloudIntoHandle() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = LibraryTab,
                    onTabSelected = {},
                    nowBar = {
                        NowBar(
                            state = nowBarEmptyState().let { state ->
                                state.copy(
                                    track = state.track.copy(
                                        title = "Scroll cloud track",
                                        canExpand = true
                                    ),
                                    labels = state.labels.copy(play = "Play", pause = "Pause")
                                )
                            },
                            waveformExpanded = false,
                            onExpandWaveform = {},
                            onCollapseWaveform = {},
                            onPrevious = Runnable {},
                            onPlayPause = Runnable {},
                            onNext = Runnable {},
                            onFavorite = Runnable {},
                            onShuffle = Runnable {},
                            onRepeat = Runnable {},
                            onOpenNowPlaying = Runnable {},
                            onOpenQueue = Runnable {},
                            onSeek = SeekAction {}
                        )
                    }
                ) { contentModifier ->
                    Column(
                        modifier = contentModifier
                            .testTag("scroll-cloud-list")
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("scroll-cloud-content")
                        Spacer(Modifier.height(1200.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithText("Scroll cloud track").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()

        composeRule.onNodeWithTag("scroll-cloud-list").performTouchInput {
            swipeUp(durationMillis = 180L)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("显示流体云").assertIsDisplayed()

        composeRule.onNodeWithTag("scroll-cloud-list").performTouchInput {
            swipeDown(durationMillis = 180L)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun dockedNowBarUsesItsCapsuleBoundsAtBottomRight() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = LibraryTab,
                    onTabSelected = {},
                    nowBar = {
                        NowBar(
                            state = nowBarEmptyState().let { state ->
                                state.copy(
                                    track = state.track.copy(
                                        title = "Dock track",
                                        subtitle = "Artist",
                                        canExpand = true
                                    ),
                                    progress = state.progress.copy(
                                        positionMs = 25_000L,
                                        durationMs = 100_000L
                                    ),
                                    labels = state.labels.copy(
                                        play = "Play",
                                        pause = "Pause",
                                        playbackProgress = "Playback progress",
                                        expandWaveform = "Expand waveform",
                                        expandNowBar = "Expand Now Bar",
                                        expandTopCloud = "Expand cloud",
                                        compactTopCloud = "Compact cloud"
                                    )
                                )
                            },
                            waveformExpanded = false,
                            onExpandWaveform = {},
                            onCollapseWaveform = {},
                            onPrevious = Runnable {},
                            onPlayPause = Runnable {},
                            onNext = Runnable {},
                            onFavorite = Runnable {},
                            onShuffle = Runnable {},
                            onRepeat = Runnable {},
                            onOpenNowPlaying = Runnable {},
                            onOpenQueue = Runnable {},
                            onSeek = SeekAction {}
                        )
                    }
                ) { contentModifier ->
                    Column(contentModifier) {
                        Text("content")
                    }
                }
            }
        }

        val initialContentTop = composeRule.onNodeWithText("content")
            .fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithText("Dock track").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        val directTopBounds = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(directTopBounds.top > 0f)
        val compactTopContentTop = composeRule.onNodeWithText("content")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(compactTopContentTop > initialContentTop)

        val compactCloudWidth = composeRule.onNodeWithContentDescription("Expand cloud")
            .fetchSemanticsNode().boundsInRoot.width
        composeRule.onNodeWithContentDescription("Expand cloud").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top-cloud-artwork").assertIsDisplayed()
        val expandedTopContentTop = composeRule.onNodeWithText("content")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(expandedTopContentTop > compactTopContentTop)
        val expandedCloudWidth = composeRule.onNodeWithContentDescription("Compact cloud")
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(expandedCloudWidth > compactCloudWidth)
        composeRule.onNodeWithContentDescription("Compact cloud").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Dock track").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.15f, y))
            moveTo(Offset(visibleSize.width * 0.90f, y), 280L)
            up()
        }
        composeRule.waitForIdle()

        val capsuleControl = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot
        val rightTab = composeRule.onNodeWithContentDescription("设置")
            .fetchSemanticsNode().boundsInRoot
        val verticalGap = rightTab.top - capsuleControl.bottom
        assertTrue(verticalGap in 0f..12f)
        assertTrue(capsuleControl.right >= rightTab.right)

        val rightDockCenter = capsuleControl.center.x
        composeRule.onNodeWithContentDescription("Expand Now Bar").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(visibleSize.width * 0.90f, y))
            moveTo(Offset(visibleSize.width * 0.10f, y), 280L)
            up()
        }
        composeRule.waitForIdle()

        val leftDockBounds = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot
        val leftDockCenter = leftDockBounds.center.x
        assertTrue(leftDockCenter < rightDockCenter)

        composeRule.onNodeWithContentDescription("Expand Now Bar").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        val topCloudBounds = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(topCloudBounds.top > 0f)
        assertTrue(topCloudBounds.bottom < leftDockBounds.top)

        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("显示流体云").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("显示流体云").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Expand Now Bar").assertCountEquals(0)

        composeRule.onNodeWithText("Dock track").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x + 120f, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        val returnedRightDockCenter = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(returnedRightDockCenter > leftDockCenter)

        composeRule.onNodeWithContentDescription("Expand Now Bar").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Expand Now Bar").assertCountEquals(0)

        composeRule.onNodeWithText("Dock track").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x, center.y - 180f), 300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Expand cloud").performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            down(center)
            moveTo(Offset(center.x - 120f, center.y + 180f), 300L)
            up()
        }
        composeRule.waitForIdle()

        val diagonalLeftDockCenter = composeRule.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(diagonalLeftDockCenter < returnedRightDockCenter)
    }

    @Test
    fun bottomNavClick_emitsSelectedTab() {
        val selected = mutableListOf<TabRoute>()
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = { selected.add(it) },
                    nowBar = {}
                ) { Text("content") }
            }
        }

        composeRule.onNodeWithText("设置").performClick()

        assertEquals(1, selected.size)
        assertEquals(SettingsTab.route, selected.first().route)
    }

    @Test
    fun floatingChromeBlocksClicksFromReachingContentBehindIt() {
        var contentClicks = 0
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {
                        NowBar(
                            state = nowBarEmptyState().let { state ->
                                state.copy(track = state.track.copy(title = "Touch shield"))
                            },
                            waveformExpanded = false,
                            onExpandWaveform = {},
                            onCollapseWaveform = {},
                            onPrevious = Runnable {},
                            onPlayPause = Runnable {},
                            onNext = Runnable {},
                            onFavorite = Runnable {},
                            onShuffle = Runnable {},
                            onRepeat = Runnable {},
                            onOpenNowPlaying = Runnable {},
                            onOpenQueue = Runnable {},
                            onSeek = SeekAction {}
                        )
                    }
                ) { contentModifier ->
                    Box(
                        modifier = contentModifier.clickable { contentClicks += 1 }
                    )
                }
            }
        }

        val nowBarBounds = composeRule.onNodeWithTag("echo-now-bar-surface")
            .fetchSemanticsNode().boundsInRoot
        val bottomNavBounds = composeRule.onNodeWithTag("echo-bottom-nav-surface")
            .fetchSemanticsNode().boundsInRoot

        composeRule.onRoot().performTouchInput {
            click(Offset(nowBarBounds.left + 2f, nowBarBounds.top + 2f))
            click(Offset(bottomNavBounds.left + 2f, bottomNavBounds.top + 2f))
        }
        composeRule.waitForIdle()

        assertEquals(0, contentClicks)
    }

    @Test
    fun emptyTabs_rendersNoBottomNav() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = emptyList(),
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {}
                ) { Text("only-content") }
            }
        }

        composeRule.onNodeWithText("only-content").assertIsDisplayed()
    }

    @Test
    fun scaffold_rendersTopBarSlot() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {},
                    topBar = { Text("top-bar-slot") }
                ) { Text("content") }
            }
        }

        composeRule.onNodeWithText("top-bar-slot").assertIsDisplayed()
    }

    @Test
    fun scaffold_topBarDefaultsEmpty_contentStillRenders() {
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoScaffold(
                    tabs = tabs,
                    selectedTab = HomeTab,
                    onTabSelected = {},
                    nowBar = {}
                ) { Text("no-topbar-content") }
            }
        }

        composeRule.onNodeWithText("no-topbar-content").assertIsDisplayed()
    }

    @Test
    fun customBackgroundCanBeSuppressedForImmersiveContent() {
        var customBackgroundVisible by mutableStateOf(true)
        composeRule.setContent {
            EchoTheme.EchoTheme {
                EchoPageBackground(
                    backgroundUri = "content://background/player",
                    customBackgroundVisible = customBackgroundVisible
                ) {
                    Text(
                        if (LocalEchoCustomBackground.current) {
                            "custom-background-active"
                        } else {
                            "custom-background-suppressed"
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithText("custom-background-active").assertIsDisplayed()

        customBackgroundVisible = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("custom-background-suppressed").assertIsDisplayed()
    }

    @Test
    fun tabRoute_fromKeyRoundTrips() {
        TabRoute.all.forEach { tab ->
            assertEquals(tab, TabRoute.fromKey(tab.route))
        }
        assertTrue(TabRoute.fromKey("nonexistent-route") == null)
    }
}
