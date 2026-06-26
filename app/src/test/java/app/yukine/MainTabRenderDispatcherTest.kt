package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabRenderDispatcherTest {
    @Test
    fun dispatcherKeepsExistingSelectedTabMapping() {
        val calls = mutableListOf<String>()
        val dispatcher = MainTabRenderDispatcher(
            renderHomeAction = Runnable { calls += "home" },
            renderLibraryAction = Runnable { calls += "library" },
            renderCollectionsAction = Runnable { calls += "collections" },
            renderQueueAction = Runnable { calls += "queue" },
            renderNetworkAction = Runnable { calls += "network" },
            renderSettingsAction = Runnable { calls += "settings" },
            renderSearchAction = Runnable { calls += "search" }
        )

        dispatcher.render(MainRoutes.TAB_HOME)
        dispatcher.render(MainRoutes.TAB_LIBRARY)
        dispatcher.render(MainRoutes.TAB_COLLECTIONS)
        dispatcher.render(MainRoutes.TAB_QUEUE)
        dispatcher.render(MainRoutes.TAB_NETWORK)
        dispatcher.render(MainRoutes.TAB_SEARCH)
        dispatcher.render(MainRoutes.TAB_NOW)
        dispatcher.render("missing")

        assertEquals(
            listOf("home", "library", "collections", "queue", "network", "search", "settings", "settings"),
            calls
        )
    }
}
