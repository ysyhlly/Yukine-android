package app.echo.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadLyricsSettingsUseCaseTest {
    @Test
    fun loadsOnlineLyricsAndOffsetTogether() {
        val operations = FakeLyricsSettingsOperations(
            onlineLyricsEnabled = true,
            lyricsOffsetMs = 1200L
        )

        val result = LoadLyricsSettingsUseCase(operations).execute()

        assertTrue(result.onlineLyricsEnabled)
        assertEquals(1200L, result.lyricsOffsetMs)
        assertEquals(listOf("online", "offset"), operations.events)
    }

    @Test
    fun preservesDisabledOnlineLyricsSetting() {
        val operations = FakeLyricsSettingsOperations(
            onlineLyricsEnabled = false,
            lyricsOffsetMs = -300L
        )

        val result = LoadLyricsSettingsUseCase(operations).execute()

        assertFalse(result.onlineLyricsEnabled)
        assertEquals(-300L, result.lyricsOffsetMs)
    }

    private class FakeLyricsSettingsOperations(
        private val onlineLyricsEnabled: Boolean,
        private val lyricsOffsetMs: Long
    ) : LyricsSettingsOperations {
        val events = mutableListOf<String>()

        override fun loadOnlineLyricsEnabled(): Boolean {
            events.add("online")
            return onlineLyricsEnabled
        }

        override fun loadLyricsOffsetMs(): Long {
            events.add("offset")
            return lyricsOffsetMs
        }
    }
}
