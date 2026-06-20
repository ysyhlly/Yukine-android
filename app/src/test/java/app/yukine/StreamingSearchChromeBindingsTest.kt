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
        var state: StreamingSearchChromeState? = null
        val bindings = StreamingSearchChromeBindings(
            StreamingSearchChromeSink { state = it }
        )

        bindings.publishStreamingSearchChrome(labels, actions)

        assertSame(labels, state?.labels)
        assertSame(actions, state?.actions)
    }
}
