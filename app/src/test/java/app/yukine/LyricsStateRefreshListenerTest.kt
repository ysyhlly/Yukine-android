package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsStateRefreshListenerTest {
    @Test
    fun stateChangesOutsideNowTabOnlyRefreshNowBar() {
        val calls = mutableListOf<String>()
        val listener = LyricsStateRefreshListener(
            selectedTabProvider = SettingsSelectedTabProvider { MainRoutes.TAB_LIBRARY },
            renderNowBarAction = Runnable { calls += "nowbar" },
            updateNowPlayingContentAction = LyricsNowPlayingContentUpdater {
                calls += "updateNow"
                true
            },
            renderSelectedTabAction = Runnable { calls += "render" }
        )

        listener.onLyricsStateChanged()

        assertEquals(listOf("nowbar"), calls)
    }

    @Test
    fun stateChangesOnNowTabRenderSelectedTabOnlyWhenContentUpdateFails() {
        val calls = mutableListOf<String>()
        var updateResult = true
        val listener = LyricsStateRefreshListener(
            selectedTabProvider = SettingsSelectedTabProvider { MainRoutes.TAB_NOW },
            renderNowBarAction = Runnable { calls += "nowbar" },
            updateNowPlayingContentAction = LyricsNowPlayingContentUpdater {
                calls += "updateNow"
                updateResult
            },
            renderSelectedTabAction = Runnable { calls += "render" }
        )

        listener.onLyricsStateChanged()
        updateResult = false
        listener.onLyricsStateChanged()

        assertEquals(
            listOf(
                "nowbar",
                "updateNow",
                "nowbar",
                "updateNow",
                "render"
            ),
            calls
        )
    }
}
