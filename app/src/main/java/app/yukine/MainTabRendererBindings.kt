package app.yukine

internal class MainTabRendererBindings(
    private val renderHomeAction: Runnable,
    private val renderLibraryAction: Runnable,
    private val renderCollectionsAction: Runnable,
    private val renderQueueAction: Runnable,
    private val renderNowPlayingAction: Runnable,
    private val renderNetworkAction: Runnable,
    private val renderSettingsAction: Runnable
) : MainTabRenderDispatcher.Renderer {
    override fun renderHome() {
        renderHomeAction.run()
    }

    override fun renderLibrary() {
        renderLibraryAction.run()
    }

    override fun renderCollections() {
        renderCollectionsAction.run()
    }

    override fun renderQueue() {
        renderQueueAction.run()
    }

    override fun renderNowPlaying() {
        renderNowPlayingAction.run()
    }

    override fun renderNetwork() {
        renderNetworkAction.run()
    }

    override fun renderSettings() {
        renderSettingsAction.run()
    }
}
