package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingGatewayEventControllerTest {
    @Test
    fun appliedGatewayRendersAndPublishesLocalizedStatus() {
        val calls = mutableListOf<String>()
        val controller = StreamingGatewayEventController(
            StreamingViewModel(),
            languageModeProvider = StatusLanguageModeProvider { AppLanguage.MODE_CHINESE },
            renderSelectedTabAction = Runnable { calls += "render" },
            statusSink = SettingsStatusSink { calls += "status:$it" }
        )

        controller.onStreamingGatewayApplied("https://gateway.example")

        assertEquals(
            listOf(
                "render",
                "status:${AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.gateway.applied")}https://gateway.example"
            ),
            calls
        )
    }
}
