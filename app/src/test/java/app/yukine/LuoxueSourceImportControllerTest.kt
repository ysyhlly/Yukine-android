package app.yukine

import android.net.Uri
import app.yukine.streaming.LuoxueImportedSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoxueSourceImportControllerTest {
    @Test
    fun opensFilePickerWhenAvailable() {
        val fixture = Fixture()

        fixture.controller.openFilePicker()

        assertEquals(1, fixture.picker!!.opens)
        assertEquals(emptyList<String>(), fixture.statuses)
    }

    @Test
    fun publishesUnavailableStatusWhenPickerIsMissing() {
        val fixture = Fixture(picker = null)

        fixture.controller.openFilePicker()

        assertEquals(listOf("streaming.lx.import.failed"), fixture.statuses)
    }

    @Test
    fun importsSelectedUrisOnIoExecutorAndPostsCompletion() {
        val sourceUri = Uri.parse("content://source/test.js")
        val fixture = Fixture(
            documents = mapOf(
                sourceUri to """
                    const sourceInfo = { name: 'LX One', version: '1' }
                    globalThis.lx.send(EVENT_NAMES.inited, { sources: { kw: {} } })
                """.trimIndent()
            )
        )

        fixture.controller.importSelectedUris(listOf(sourceUri))

        assertEquals(listOf("streaming.lx.importing", "streaming.lx.source.imported1"), fixture.statuses)
        assertEquals(listOf("io"), fixture.executorCalls)
        assertEquals(listOf("main"), fixture.posterCalls)
        assertEquals(1, fixture.savedSources.size)
        assertEquals("LX One", fixture.savedSources.single().name)
        assertEquals(1, fixture.completions)
    }

    @Test
    fun reportsEmptySelectionWithoutStartingImport() {
        val fixture = Fixture()

        fixture.controller.importSelectedUris(emptyList())

        assertEquals(listOf("streaming.lx.source.none"), fixture.statuses)
        assertEquals(emptyList<String>(), fixture.executorCalls)
    }

    @Test
    fun reportsEmptyNetworkUrlWithoutStartingImport() {
        val fixture = Fixture()

        fixture.controller.importFromUrls("not a url")

        assertEquals(listOf("streaming.lx.source.url.empty"), fixture.statuses)
        assertEquals(emptyList<String>(), fixture.executorCalls)
    }

    @Test
    fun saveImportedSourcesIncludesFailedCountAndCompletion() {
        val fixture = Fixture()

        fixture.controller.saveImportedSources(
            listOf(
                LuoxueImportedSource(
                    id = "source-1",
                    name = "LX One",
                    script = "globalThis.lx"
                )
            ),
            failedCount = 2
        )

        assertEquals(listOf("streaming.lx.source.imported1，streaming.lx.source.failed2"), fixture.statuses)
        assertEquals(1, fixture.completions)
    }

    @Test
    fun saveImportedSourcesReportsNoValidSourceWhenNothingParsedOrFailed() {
        val fixture = Fixture()

        fixture.controller.saveImportedSources(emptyList(), failedCount = 0)

        assertEquals(listOf("streaming.lx.source.none"), fixture.statuses)
        assertEquals(1, fixture.completions)
    }

    private class Fixture(
        val picker: FakePicker? = FakePicker(),
        private val documents: Map<Uri, String> = emptyMap()
    ) {
        val statuses = mutableListOf<String>()
        val executorCalls = mutableListOf<String>()
        val posterCalls = mutableListOf<String>()
        val savedSources = mutableListOf<LuoxueImportedSource>()
        var completions = 0

        val controller = LuoxueSourceImportController(
            textProvider = LuoxueSourceImportTextProvider { key -> key },
            documentPickerProvider = LuoxueSourceDocumentPickerProvider { picker },
            documentReader = LuoxueSourceDocumentReader { uri -> documents[uri] },
            sourceStore = LuoxueSourceStoreWriter { sources ->
                savedSources += sources
                sources.size
            },
            ioExecutor = LuoxueSourceTaskExecutor { task ->
                executorCalls += "io"
                task.run()
            },
            networkExecutor = LuoxueSourceTaskExecutor { task ->
                executorCalls += "network"
                task.run()
            },
            mainPoster = LuoxueSourceTaskPoster { task ->
                posterCalls += "main"
                task.run()
            },
            statusSink = LuoxueSourceImportStatusSink { status -> statuses += status },
            completionAction = LuoxueSourceImportCompletionAction { completions += 1 }
        )
    }

    private class FakePicker : LuoxueSourceFilePicker {
        var opens = 0

        override fun openLuoxueSourceFilePicker() {
            opens += 1
        }
    }

}
