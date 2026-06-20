package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabRendererBindingsTest {
    @Test
    fun forwardsRenderCallbacksToBoundActions() {
        val calls = mutableListOf<String>()
        val renderer = MainTabRendererBindings(
            renderHomeAction = Runnable { calls += "home" },
            renderLibraryAction = Runnable { calls += "library" },
            renderCollectionsAction = Runnable { calls += "collections" },
            renderQueueAction = Runnable { calls += "queue" },
            renderNowPlayingAction = Runnable { calls += "now" },
            renderNetworkAction = Runnable { calls += "network" },
            renderSettingsAction = Runnable { calls += "settings" }
        )

        renderer.renderHome()
        renderer.renderLibrary()
        renderer.renderCollections()
        renderer.renderQueue()
        renderer.renderNowPlaying()
        renderer.renderNetwork()
        renderer.renderSettings()

        assertEquals(
            listOf("home", "library", "collections", "queue", "now", "network", "settings"),
            calls
        )
    }

    @Test
    fun dispatcherKeepsExistingSelectedTabMapping() {
        val calls = mutableListOf<String>()
        val dispatcher = MainTabRenderDispatcher(
            MainTabRendererBindings(
                renderHomeAction = Runnable { calls += "home" },
                renderLibraryAction = Runnable { calls += "library" },
                renderCollectionsAction = Runnable { calls += "collections" },
                renderQueueAction = Runnable { calls += "queue" },
                renderNowPlayingAction = Runnable { calls += "now" },
                renderNetworkAction = Runnable { calls += "network" },
                renderSettingsAction = Runnable { calls += "settings" }
            )
        )

        dispatcher.render(MainRoutes.TAB_HOME)
        dispatcher.render(MainRoutes.TAB_LIBRARY)
        dispatcher.render(MainRoutes.TAB_COLLECTIONS)
        dispatcher.render(MainRoutes.TAB_QUEUE)
        dispatcher.render(MainRoutes.TAB_NETWORK)
        dispatcher.render(MainRoutes.TAB_NOW)
        dispatcher.render("missing")

        assertEquals(
            listOf("home", "library", "collections", "queue", "network", "settings", "settings"),
            calls
        )
    }
}
