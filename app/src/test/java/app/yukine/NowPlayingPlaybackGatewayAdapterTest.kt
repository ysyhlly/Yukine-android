package app.yukine

import app.yukine.playback.PlaybackServiceActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NowPlayingPlaybackGatewayAdapterTest {
    @Test
    fun factoryCreatesGatewayThatStartsPlaybackServiceThroughStarter() {
        val startedActions = mutableListOf<String?>()
        val factory = MainNowPlayingPlaybackGatewayFactory(
            { action -> startedActions += action }
        )
        val gateway = factory.create { null }

        assertFalse(gateway.serviceConnected())
        gateway.startPlaybackService(PlaybackServiceActions.NEXT)
        gateway.startPlaybackService(null)

        assertEquals(listOf(PlaybackServiceActions.NEXT, null), startedActions)
    }
}
