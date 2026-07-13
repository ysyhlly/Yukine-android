package app.yukine

internal class MainTabRenderDispatcher(
    private val renderLibraryAction: Runnable,
    private val renderCollectionsAction: Runnable,
    private val renderNetworkAction: Runnable,
    private val renderSettingsAction: Runnable,
    private val renderSearchAction: Runnable
) {
    fun render(selectedTab: String) {
        when (selectedTab) {
            MainRoutes.TAB_HOME -> Unit
            MainRoutes.TAB_LIBRARY -> renderLibraryAction.run()
            MainRoutes.TAB_COLLECTIONS -> renderCollectionsAction.run()
            MainRoutes.TAB_QUEUE -> Unit
            MainRoutes.TAB_NETWORK -> renderNetworkAction.run()
            MainRoutes.TAB_SEARCH -> renderSearchAction.run()
            else -> renderSettingsAction.run()
        }
    }
}
