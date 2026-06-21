package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackActionResultControllerTest {
    @Test
    fun appliesAllPlaybackResultEdgesInOrder() {
        val listener = FakeListener()
        val controller = PlaybackActionResultController(listener)
        val snapshot = PlaybackStateSnapshot.empty()

        controller.apply(
            PlaybackActionResultUi(
                snapshot = snapshot,
                status = " Playing ",
                publishPlaybackState = true,
                renderSelectedTab = true,
                renderNowBar = true,
                navigateNow = true
            )
        )

        assertEquals(
            listOf("snapshot", "status: Playing ", "publish", "nowBar", "tab", "navigateNow"),
            listener.calls
        )
        assertEquals(snapshot, listener.snapshots.single())
    }

    @Test
    fun ignoresNullAndBlankStatus() {
        val listener = FakeListener()
        val controller = PlaybackActionResultController(listener)

        controller.apply(null)
        controller.apply(
            PlaybackActionResultUi(
                snapshot = null,
                status = "   ",
                publishPlaybackState = false,
                renderSelectedTab = false,
                renderNowBar = false,
                navigateNow = false
            )
        )

        assertEquals(emptyList<String>(), listener.calls)
        assertEquals(emptyList<PlaybackStateSnapshot>(), listener.snapshots)
    }

    private class FakeListener : PlaybackActionResultController.Listener {
        val calls = mutableListOf<String>()
        val snapshots = mutableListOf<PlaybackStateSnapshot>()

        override fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot) {
            calls += "snapshot"
            snapshots += snapshot
        }

        override fun setStatus(status: String) {
            calls += "status:$status"
        }

        override fun publishPlaybackState() {
            calls += "publish"
        }

        override fun renderNowBar() {
            calls += "nowBar"
        }

        override fun renderSelectedTab() {
            calls += "tab"
        }

        override fun navigateNow() {
            calls += "navigateNow"
        }
    }
}
