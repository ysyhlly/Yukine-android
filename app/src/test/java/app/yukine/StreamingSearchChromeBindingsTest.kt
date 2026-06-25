package app.yukine

import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import org.junit.Assert.assertSame
import org.junit.Test

class StreamingSearchChromeBindingsTest {
    @Test
    fun publishesStreamingSearchChromeState() {
        val labels = StreamingSearchLabels.empty()
        val actions = StreamingSearchActions.empty()
        var publishedLabels: StreamingSearchLabels? = null
        var publishedActions: StreamingSearchActions? = null
        val bindings = StreamingSearchChromeBindings(
            StreamingSearchChromeSink { nextLabels, nextActions ->
                publishedLabels = nextLabels
                publishedActions = nextActions
            }
        )

        bindings.publishStreamingSearchChrome(labels, actions)

        assertSame(labels, publishedLabels)
        assertSame(actions, publishedActions)
    }
}
