package app.yukine

internal class MainTabRenderDispatcher(
    private val renderLibraryAction: Runnable,
    private val renderCollectionsAction: Runnable,
    private val renderNetworkAction: Runnable
) {
    fun render(selectedTab: String) {
        when (selectedTab) {
            MainRoutes.TAB_HOME -> Unit
            MainRoutes.TAB_LIBRARY -> renderLibraryAction.run()
            MainRoutes.TAB_COLLECTIONS -> renderCollectionsAction.run()
            MainRoutes.TAB_QUEUE -> Unit
            MainRoutes.TAB_NETWORK -> renderNetworkAction.run()
            else -> Unit
        }
    }
}
