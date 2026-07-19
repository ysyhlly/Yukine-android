package app.yukine

import app.yukine.navigation.LibraryTab
import app.yukine.navigation.NetworkTab
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.TabRoute

/** Owns main-shell navigation intent policy without owning a second route state. */
internal class MainNavigationIntentOwner(
    private val routeController: MainRouteController,
    private val requestSettingsScrollToTop: Runnable
) {
    @JvmOverloads
    fun navigateToTab(tab: TabRoute, userInitiated: Boolean = false) {
        val previousDirectory = currentDirectoryKey()
        val sameTab = routeController.navigateToTab(tab, userInitiated)
        if (userInitiated && sameTab && previousDirectory == currentDirectoryKey()) {
            requestCurrentDirectoryScrollToTop()
        }
    }

    fun handleBack(): Boolean = routeController.applyBackNavigation().handled

    private fun currentDirectoryKey(): DirectoryKey {
        val route = routeController.current()
        return when (route.selectedTab) {
            NetworkTab -> DirectoryKey(
                tab = route.selectedTab,
                networkPage = route.networkPage,
                selectedRemoteSourceId = route.selectedRemoteSourceId
            )

            SettingsTab -> DirectoryKey(
                tab = route.selectedTab,
                settingsPage = route.settingsPage
            )

            LibraryTab -> DirectoryKey(
                tab = route.selectedTab,
                libraryPage = route.libraryPage,
                libraryMode = route.libraryMode,
                selectedLibraryGroupKey = route.selectedLibraryGroupKey,
                selectedPlaylistId = route.selectedPlaylistId
            )

            else -> DirectoryKey(tab = route.selectedTab)
        }
    }

    private fun requestCurrentDirectoryScrollToTop() {
        if (routeController.current().selectedTab == SettingsTab) {
            requestSettingsScrollToTop.run()
        }
    }

    private data class DirectoryKey(
        val tab: TabRoute,
        val libraryPage: LibraryPage? = null,
        val libraryMode: String? = null,
        val selectedLibraryGroupKey: String? = null,
        val selectedPlaylistId: Long? = null,
        val networkPage: NetworkPage? = null,
        val selectedRemoteSourceId: Long? = null,
        val settingsPage: SettingsPage? = null
    )
}
