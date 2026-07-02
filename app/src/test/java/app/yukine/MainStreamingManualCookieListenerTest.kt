package app.yukine

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainStreamingManualCookieListenerTest {
    @Test
    fun delegatesManualCookieCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val dialogState = manualCookieDialogState(StreamingProviderName.QQ_MUSIC)
        val listener = MainStreamingManualCookieListener(
            selectedProviderSource = ManualCookieSelectedProviderSource {
                calls += "selected"
                StreamingProviderName.QQ_MUSIC
            },
            dialogPresenter = ManualCookieDialogPresenter {
                calls += "dialog:${it.provider?.wireName}"
                assertSame(dialogState, it)
            },
            loginSuccessHandler = ManualCookieLoginSuccessHandler {
                calls += "login:${it.wireName}"
            },
            statusSink = ManualCookieStatusSink {
                calls += "status:$it"
            }
        )

        assertEquals(StreamingProviderName.QQ_MUSIC, listener.selectedProvider())
        listener.showManualCookieDialog(dialogState)
        listener.onStreamingLoginSuccess(StreamingProviderName.NETEASE)
        listener.setStatus("Saved")

        assertEquals(listOf("selected", "dialog:qqmusic", "login:netease", "status:Saved"), calls)
    }

    @Test
    fun factoryCreatesStreamingManualCookieControllerListener() {
        val calls = mutableListOf<String>()
        val listener = StreamingModule.provideMainStreamingManualCookieListenerFactory().create(
            ManualCookieSelectedProviderSource { StreamingProviderName.KUGOU },
            ManualCookieDialogPresenter { calls += "dialog:${it.provider?.wireName}" },
            ManualCookieLoginSuccessHandler { calls += "login:${it.wireName}" },
            ManualCookieStatusSink { calls += "status:$it" }
        )

        assertEquals(StreamingProviderName.KUGOU, listener.selectedProvider())
        listener.showManualCookieDialog(manualCookieDialogState(StreamingProviderName.KUGOU))
        listener.onStreamingLoginSuccess(StreamingProviderName.KUGOU)
        listener.setStatus("Ready")

        assertEquals(listOf("dialog:kugou", "login:kugou", "status:Ready"), calls)
    }
}

private fun manualCookieDialogState(provider: StreamingProviderName): StreamingManualCookieDialogState =
    StreamingManualCookieDialogState(
        provider = provider,
        title = "Cookie",
        hint = "cookie",
        unavailable = false,
        unavailableStatus = ""
    )
