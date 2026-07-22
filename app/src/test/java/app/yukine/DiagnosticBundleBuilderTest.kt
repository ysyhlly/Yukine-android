package app.yukine

import androidx.test.core.app.ApplicationProvider
import app.yukine.diagnostics.DiagnosticLogFile
import app.yukine.diagnostics.DiagnosticLogSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticBundleBuilderTest {
    @Test
    fun buildsManifestEventsAndCrashesWithExportTimeRedaction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val builder = DiagnosticBundleBuilder(
            eventSnapshotProvider = {
                DiagnosticLogSnapshot(
                    listOf(
                        DiagnosticLogFile(
                            "events-current.jsonl",
                            10L,
                            "{\"message\":\"Authorization: Bearer event-secret\"}".toByteArray()
                        )
                    ),
                    droppedEvents = 2L,
                    complete = true
                )
            },
            crashSnapshotProvider = {
                listOf(
                    DiagnosticSourceFile(
                        "crash-1.log",
                        20L,
                        "password=crash-secret\nstack".toByteArray()
                    )
                )
            },
            nowMs = { 1_700_000_000_000L }
        )
        val output = File(context.cacheDir, "diagnostic-test.zip")

        assertTrue(builder.build(context, output))

        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("diagnostics.json"))
            assertNotNull(zip.getEntry("README.txt"))
            val event = zip.readText("events/events-current.jsonl")
            val crash = zip.readText("crashes/crash-1.log")
            assertTrue(event.contains("<redacted>"))
            assertTrue(crash.contains("<redacted>"))
            assertFalse(event.contains("event-secret"))
            assertFalse(crash.contains("crash-secret"))
            assertTrue(zip.readText("diagnostics.json").contains("\"dropped_events\": 2"))
        }
    }

    @Test
    fun emptySourcesStillProduceReadablePackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val builder = DiagnosticBundleBuilder(
            eventSnapshotProvider = { DiagnosticLogSnapshot(emptyList(), 0L, true) },
            crashSnapshotProvider = { emptyList() }
        )
        val output = File(context.cacheDir, "diagnostic-empty.zip")

        assertTrue(builder.build(context, output))

        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("diagnostics.json"))
            assertNotNull(zip.getEntry("README.txt"))
        }
    }

    private fun ZipFile.readText(name: String): String =
        getInputStream(getEntry(name)).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
