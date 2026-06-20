package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackActionResultBindingsTest {
    @Test
    fun forwardsPlaybackResultEdgesToActivityBindings() {
        val calls = mutableListOf<String>()
        val snapshot = PlaybackStateSnapshot.empty()
        val bindings = PlaybackActionResultBindings(
            snapshotReplacer = PlaybackSnapshotReplacer { calls += "snapshot:${it.durationMs}" },
            statusSink = QueueStatusSink { calls += "status:$it" },
            publishPlaybackStateAction = QueueNoArgAction { calls += "publish" },
            renderNowBarAction = QueueNoArgAction { calls += "nowBar" },
            renderSelectedTabAction = QueueNoArgAction { calls += "tab" },
            routeNavigator = PlaybackRouteNavigator { calls += "navigateNow" }
        )

        bindings.replacePlaybackSnapshot(snapshot)
        bindings.setStatus("ready")
        bindings.publishPlaybackState()
        bindings.renderNowBar()
        bindings.renderSelectedTab()
        bindings.navigateNow()

        assertEquals(
            listOf("snapshot:0", "status:ready", "publish", "nowBar", "tab", "navigateNow"),
            calls
        )
    }
}
