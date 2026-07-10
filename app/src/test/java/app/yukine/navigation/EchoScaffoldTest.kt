package app.yukine.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoPageBackground
import app.yukine.ui.LocalEchoCustomBackground
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
