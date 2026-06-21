package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingGatewayHostBindingsTest {
    @Test
    fun forwardsGatewayHostCallbacksToBoundActions() {
        val calls = mutableListOf<String>()
        val host = StreamingGatewayHostBindings(
            languageModeProvider = StatusLanguageModeProvider { AppLanguage.MODE_CHINESE },
            renderSelectedTabAction = Runnable { calls += "render" },
            statusSink = SettingsStatusSink { calls += "status:$it" }
        )

        host.renderSelectedTab()
        host.setStatus("applied")

        assertEquals(AppLanguage.MODE_CHINESE, host.languageMode())
        assertEquals(listOf("render", "status:applied"), calls)
    }
}
