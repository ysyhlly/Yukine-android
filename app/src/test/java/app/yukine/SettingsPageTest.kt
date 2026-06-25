package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPageTest {
    @Test
    fun fromRouteMapsEveryKnownSettingsRoute() {
        for (page in SettingsPage.all) {
            assertEquals(page, SettingsPage.fromRoute(page.route))
            assertEquals(page.route, SettingsPage.route(page))
        }
    }

    @Test
    fun fromRouteFallsBackToHomeForUnknownRoutes() {
        assertEquals(SettingsPage.Home, SettingsPage.fromRoute("missing"))
        assertEquals(SettingsPage.Home, SettingsPage.fromRoute(null))
    }
}
