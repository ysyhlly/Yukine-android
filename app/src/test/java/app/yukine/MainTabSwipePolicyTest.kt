package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainTabSwipePolicyTest {
    @Test
    fun adjacentTabMovesBetweenNeighboringTabs() {
        val tabs = tabs()

        assertEquals(MainRoutes.TAB_SETTINGS, MainTabSwipePolicy.adjacentTab(tabs, MainRoutes.TAB_COLLECTIONS, true))
        assertEquals(MainRoutes.TAB_COLLECTIONS, MainTabSwipePolicy.adjacentTab(tabs, MainRoutes.TAB_SETTINGS, false))
    }

    @Test
    fun adjacentTabStopsAtEdgesAndUnknownTabs() {
        val tabs = tabs()

        assertNull(MainTabSwipePolicy.adjacentTab(tabs, MainRoutes.TAB_HOME, false))
        assertNull(MainTabSwipePolicy.adjacentTab(tabs, MainRoutes.TAB_SETTINGS, true))
        assertNull(MainTabSwipePolicy.adjacentTab(tabs, "missing", true))
        assertNull(MainTabSwipePolicy.adjacentTab(emptyList(), MainRoutes.TAB_LIBRARY, true))
    }

    private fun tabs(): List<String> = listOf(
        MainRoutes.TAB_HOME,
        MainRoutes.TAB_LIBRARY,
        MainRoutes.TAB_COLLECTIONS,
        MainRoutes.TAB_SETTINGS
    )
}
