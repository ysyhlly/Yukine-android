package app.yukine

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingManualCookieBindingsTest {
    @Test
    fun forwardsManualCookieEdges() {
        val calls = mutableListOf<String>()
        val bindings = StreamingManualCookieBindings(
            selectedProviderProvider = StreamingSelectedProviderProvider {
                calls += "provider"
                StreamingProviderName.NETEASE
            },
            dialogPresenter = StreamingManualCookieDialogPresenter { state ->
                calls += "dialog:${state.title}"
            },
            loginSuccessAction = StreamingManualCookieLoginSuccessAction { provider ->
                calls += "success:${provider.wireName}"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" }
        )

        val provider = bindings.selectedProvider()
        bindings.showManualCookieDialog(StreamingManualCookieDialogState(title = "Cookie"))
        bindings.onStreamingLoginSuccess(StreamingProviderName.NETEASE)
        bindings.setStatus("Saved")

        assertEquals(StreamingProviderName.NETEASE, provider)
        assertEquals(
            listOf("provider", "dialog:Cookie", "success:netease", "status:Saved"),
            calls
        )
    }
}
