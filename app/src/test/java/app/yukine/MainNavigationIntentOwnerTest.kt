package app.yukine

import androidx.lifecycle.SavedStateHandle
import app.yukine.navigation.HomeTab
import app.yukine.navigation.SettingsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainNavigationIntentOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repeatedUserNavigationToCurrentSettingsDirectoryRequestsScrollToTop() {
        val routeController = controllerWithSettingsHome()
        var scrollRequests = 0
        val owner = MainNavigationIntentOwner(routeController, Runnable { scrollRequests += 1 })

        owner.navigateToTab(SettingsTab, userInitiated = true)

        assertEquals(1, scrollRequests)
    }

    @Test
    fun navigationThatResetsSettingsDirectoryDoesNotRequestScrollToTop() {
        val routeController = controllerWithSettingsHome().also {
            it.setSettingsPage(SettingsPage.Appearance)
        }
        var scrollRequests = 0
        val owner = MainNavigationIntentOwner(routeController, Runnable { scrollRequests += 1 })

        owner.navigateToTab(SettingsTab, userInitiated = true)

        assertEquals(SettingsPage.Home, routeController.current().settingsPage)
        assertEquals(0, scrollRequests)
    }

    @Test
    fun nonUserNavigationNeverRequestsScrollToTop() {
        val routeController = controllerWithSettingsHome()
        var scrollRequests = 0
        val owner = MainNavigationIntentOwner(routeController, Runnable { scrollRequests += 1 })

        owner.navigateToTab(SettingsTab)

        assertEquals(0, scrollRequests)
    }

    @Test
    fun backDelegatesToTheSingleRouteOwner() {
        val viewModel = NavigationViewModel(SavedStateHandle())
        val routeController = MainRouteController(viewModel)
        routeController.navigateToTab(SettingsTab, userInitiated = false)
        val owner = MainNavigationIntentOwner(routeController, Runnable {})

        assertTrue(owner.handleBack())
        assertEquals(HomeTab, routeController.current().selectedTab)
        assertFalse(owner.handleBack())
    }

    private fun controllerWithSettingsHome(): MainRouteController {
        val routeController = MainRouteController(NavigationViewModel(SavedStateHandle()))
        routeController.navigateToTab(SettingsTab, userInitiated = false)
        return routeController
    }
}
