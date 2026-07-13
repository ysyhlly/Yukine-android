package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenLibraryRestoreOwnerTest {
    @Test
    fun changedRestoreReloadsLibraryAndAlwaysRefreshesSettings() {
        val calls = mutableListOf<String>()
        val owner = HiddenLibraryRestoreOwner(
            LibraryViewModel(),
            Runnable { calls += "library" },
            Runnable { calls += "settings" }
        )

        owner.onRestored(false)
        owner.onRestored(true)

        assertEquals(listOf("settings", "library", "settings"), calls)
    }
}
