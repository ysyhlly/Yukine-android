package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMessageHostBindingsTest {
    @Test
    fun forwardsLanguageModeAndRawStatusUpdates() {
        val updates = mutableListOf<String>()
        val host = StatusMessageHostBindings(
            languageModeProvider = StatusLanguageModeProvider { AppLanguage.MODE_ENGLISH },
            rawStatusUpdater = RawStatusUpdater { updates += it }
        )

        host.updateStatus("Ready")

        assertEquals(AppLanguage.MODE_ENGLISH, host.languageMode())
        assertEquals(listOf("Ready"), updates)
    }
}
