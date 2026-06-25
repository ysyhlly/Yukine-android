package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTextResolverTest {
    @Test
    fun resolvesStatusKeyWithCurrentLanguageMode() {
        val resolver = MessageTextResolver { AppLanguage.MODE_CHINESE }

        assertEquals(
            AppLanguage.text(AppLanguage.MODE_CHINESE, "backup.export.success"),
            resolver.text("backup.export.success")
        )
    }

    @Test
    fun blankStatusKeyResolvesToEmptyMessage() {
        val resolver = MessageTextResolver { AppLanguage.MODE_CHINESE }

        assertEquals("", resolver.text(null))
        assertEquals("", resolver.text("  "))
    }
}
