package app.echo.next

internal class MainTabRenderDispatcher(
    private val renderer: Renderer
) {
    interface Renderer {
        fun renderHome()

        fun renderLibrary()

        fun renderCollections()

        fun renderQueue()

        fun renderNowPlaying()

        fun renderNetwork()

        fun renderSettings()
    }

    fun render(selectedTab: String) {
        when (selectedTab) {
            MainRoutes.TAB_HOME -> renderer.renderHome()
            MainRoutes.TAB_LIBRARY -> renderer.renderLibrary()
            MainRoutes.TAB_COLLECTIONS -> renderer.renderCollections()
            MainRoutes.TAB_QUEUE -> renderer.renderQueue()
            MainRoutes.TAB_NETWORK -> renderer.renderNetwork()
            else -> renderer.renderSettings()
        }
    }
}
