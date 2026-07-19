package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReadModelStateListenerTest {
    @Test
    fun regularAndBufferingCallbacksPublishBeforeForwardingExactlyOnce() {
        val snapshot = PlaybackStateSnapshot.empty()
        val events = mutableListOf<String>()
        val downstream = object : PlaybackStateListener {
            override fun onPlaybackStateChanged(value: PlaybackStateSnapshot) {
                assertEquals(snapshot, value)
                events += "downstream:state"
            }

            override fun onPlaybackBuffering(value: PlaybackStateSnapshot) {
                assertEquals(snapshot, value)
                events += "downstream:buffering"
            }
        }
        val listener = PlaybackReadModelStateListener(
            publish = {
                assertEquals(snapshot, it)
                events += "publish"
            },
            downstream = downstream
        )

        listener.onPlaybackStateChanged(snapshot)
        listener.onPlaybackBuffering(snapshot)

        assertEquals(
            listOf("publish", "downstream:state", "publish", "downstream:buffering"),
            events
        )
    }
}
