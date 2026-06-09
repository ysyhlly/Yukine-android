package app.echo.next.ui

import app.echo.next.MainRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarControllerTest {
    @Test
    fun initialTabKeyUsesRestoredSelectedTab() {
        val tabs = appTabs()

        assertEquals(MainRoutes.TAB_NETWORK, initialTabKey(tabs, MainRoutes.TAB_NETWORK))
        assertEquals(MainRoutes.TAB_SETTINGS, initialTabKey(tabs, MainRoutes.TAB_SETTINGS))
    }

    @Test
    fun initialTabKeyFallsBackToFirstKnownTab() {
        val tabs = appTabs()

        assertEquals(MainRoutes.TAB_HOME, initialTabKey(tabs, "missing"))
        assertEquals("", initialTabKey(emptyList(), MainRoutes.TAB_SETTINGS))
    }

    private fun appTabs(): List<AppTabUiState> = listOf(
        AppTabUiState("Home", MainRoutes.TAB_HOME),
        AppTabUiState("Library", MainRoutes.TAB_LIBRARY),
        AppTabUiState("Network", MainRoutes.TAB_NETWORK),
        AppTabUiState("Settings", MainRoutes.TAB_SETTINGS)
    )
}
