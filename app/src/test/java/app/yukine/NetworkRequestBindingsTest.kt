package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRequestBindingsTest {
    @Test
    fun labelsAndStatusListenerForwardToBoundFunctions() {
        val calls = mutableListOf<String>()
        val labels = NetworkRequestLabels(LanguageTextProvider { key -> "label:$key" })
        val listener = NetworkRequestStatusListener(
            NetworkRequestStatusSink { calls += "status:$it" }
        )

        assertEquals("label:syncing", labels.text("syncing"))
        listener.setStatus("Ready")

        assertEquals(listOf("status:Ready"), calls)
    }
}
