package app.yukine.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RollingDiagnosticLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordsSanitizedJsonAndReturnsConsistentSnapshot() {
        val store = RollingDiagnosticLogStore(temporaryFolder.newFolder())

        store.record("ERROR", "Auth", "token=secret", IllegalStateException("password=hunter2"))
        val snapshot = store.snapshot()

        assertTrue(snapshot.complete)
        assertEquals(1, snapshot.files.size)
        val content = snapshot.files.single().content.toString(Charsets.UTF_8)
        assertTrue(content.contains("<redacted>"))
        assertFalse(content.contains("secret"))
        assertFalse(content.contains("hunter2"))
    }

    @Test
    fun rotatesAndRetainsOnlyConfiguredFileCount() {
        var now = 10_000L
        val store = RollingDiagnosticLogStore(
            directory = temporaryFolder.newFolder(),
            nowMs = { now++ },
            maxFileBytes = 80,
            maxFiles = 2,
            maxAgeMs = Long.MAX_VALUE
        )

        repeat(12) { index ->
            store.record("WARN", "Rotation", "event-$index-${"x".repeat(40)}", null)
        }
        val snapshot = store.snapshot()

        assertTrue(snapshot.files.size <= 2)
        assertTrue(snapshot.files.isNotEmpty())
    }
}
