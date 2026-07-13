package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.streaming.LuoxueImportedSource
import app.yukine.streaming.LuoxueSourceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoxueSourceStoreTest {
    @Test
    fun setAllEnabledPersistsEveryImportedSourceInOneUpdate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LuoxueSourceStore(context)
        val firstId = "bulk-first-${System.nanoTime()}"
        val secondId = "bulk-second-${System.nanoTime()}"
        try {
            assertEquals(
                2,
                store.saveAll(
                    listOf(
                        LuoxueImportedSource(firstId, "First", script = "first"),
                        LuoxueImportedSource(secondId, "Second", script = "second")
                    )
                )
            )

            assertTrue(store.setAllEnabled(false))
            val disabled = store.load().filter { it.id == firstId || it.id == secondId }
            assertEquals(2, disabled.size)
            assertTrue(disabled.none { it.enabled })

            assertTrue(store.setAllEnabled(true))
            val enabled = store.load().filter { it.id == firstId || it.id == secondId }
            assertTrue(enabled.all { it.enabled })
        } finally {
            store.remove(firstId)
            store.remove(secondId)
        }
    }
}
