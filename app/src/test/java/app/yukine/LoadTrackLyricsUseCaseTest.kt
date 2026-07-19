package app.yukine

import android.net.Uri
import app.yukine.data.LyricsRepository
import app.yukine.model.LyricLine
import app.yukine.model.LyricWord
import app.yukine.model.LyricsDocument
import app.yukine.model.LyricsLine
import app.yukine.model.LyricsTrack
import app.yukine.model.LyricsTrackRole
import app.yukine.model.Track
import app.yukine.streaming.LuoxueScriptLyrics
import app.yukine.streaming.LuoxueTrackMetadataResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadTrackLyricsUseCaseTest {
    @Test
    fun returnsEmptyListWhenTrackIsMissing() = runTest {
        val operations = FakeTrackLyricsOperations()

        val result = LoadTrackLyricsUseCase(operations).execute(null, onlineEnabled = true, neteaseProviderTrackId = "123")

        assertTrue(result.isEmpty())
        assertTrue(operations.events.isEmpty())
    }

    @Test
    fun delegatesLyricsLoadWithOnlineFlagAndProviderTrackId() = runTest {
        val operations = FakeTrackLyricsOperations().apply {
            result = listOf(LyricsLine(1000L, "hello"))
        }

        val result = LoadTrackLyricsUseCase(operations).execute(
            track = track(7L),
            onlineEnabled = true,
            neteaseProviderTrackId = "9988"
        )

        assertEquals(listOf("load:7:true:9988"), operations.events)
        assertEquals(listOf("hello"), result.map { it.text })
    }

    @Test
    fun normalizesMissingProviderTrackIdToEmptyString() = runTest {
        val operations = FakeTrackLyricsOperations()

        LoadTrackLyricsUseCase(operations).execute(
            track = track(9L),
            onlineEnabled = false,
            neteaseProviderTrackId = null
        )

        assertEquals(listOf("load:9:false:"), operations.events)
    }

    @Test
    fun documentLoadPreservesRichWordTiming() = runTest {
        val expected = LyricsDocument(
            format = "elrc",
            tracks = listOf(
                LyricsTrack(
                    LyricsTrackRole.PRIMARY,
                    lines = listOf(
                        LyricLine(
                            1_000L,
                            2_000L,
                            "hello",
                            listOf(LyricWord(1_000L, 2_000L, "hello", 0, 5))
                        )
                    )
                )
            )
        )
        val operations = FakeTrackLyricsOperations().apply {
            documentResult = expected
        }

        val result = LoadTrackLyricsUseCase(operations).executeDocument(
            track = track(11L),
            onlineEnabled = true,
            neteaseProviderTrackId = "rich"
        )

        assertEquals(expected, result)
        assertEquals(listOf("document:11:true:rich"), operations.events)
    }

    @Test
    fun luoxueLyricsWinBeforeTheNormalFallbackChain() = runTest {
        val fallback = FakeTrackLyricsOperations().apply {
            result = listOf(LyricsLine(2_000L, "fallback"))
        }
        val operations = LuoxueFirstTrackLyricsOperations(
            resolver = object : LuoxueTrackMetadataResolver {
                override suspend fun resolveLyrics(track: Track?): LuoxueScriptLyrics =
                    LuoxueScriptLyrics(
                        lyric = "[00:01.00]原文",
                        translation = "[00:01.00]Translation"
                    )

                override suspend fun resolveCoverUrl(track: Track?): String? = null
            },
            repository = LyricsRepository(),
            fallback = fallback
        )

        val result = operations.loadForTrack(track(13L), onlineEnabled = true, neteaseProviderTrackId = "9988")

        assertEquals(listOf("原文\nTranslation"), result.map { it.text })
        assertTrue(fallback.events.isEmpty())
    }

    @Test
    fun luoxueDocumentKeepsEnhancedWordsAndTranslationTrack() = runTest {
        val fallback = FakeTrackLyricsOperations()
        val operations = LuoxueFirstTrackLyricsOperations(
            resolver = object : LuoxueTrackMetadataResolver {
                override suspend fun resolveLyrics(track: Track?): LuoxueScriptLyrics =
                    LuoxueScriptLyrics(
                        lyric = "[00:01.00]<00:01.00>原<00:01.50>文",
                        translation = "[00:01.00]Translation"
                    )

                override suspend fun resolveCoverUrl(track: Track?): String? = null
            },
            repository = LyricsRepository(),
            fallback = fallback
        )

        val result = operations.loadDocumentForTrack(
            track(15L),
            onlineEnabled = true,
            neteaseProviderTrackId = "9988"
        )

        assertEquals(listOf("原", "文"), result.track(LyricsTrackRole.PRIMARY)!!.lines.single().words.map { it.text })
        assertEquals("Translation", result.track(LyricsTrackRole.TRANSLATION)!!.lines.single().text)
        assertTrue(fallback.events.isEmpty())
    }

    private class FakeTrackLyricsOperations : TrackLyricsOperations {
        val events = mutableListOf<String>()
        var result: List<LyricsLine> = emptyList()
        var documentResult: LyricsDocument? = null

        override suspend fun loadForTrack(
            track: Track,
            onlineEnabled: Boolean,
            neteaseProviderTrackId: String
        ): List<LyricsLine> {
            events += "load:${track.id}:$onlineEnabled:$neteaseProviderTrackId"
            return result
        }

        override suspend fun loadDocumentForTrack(
            track: Track,
            onlineEnabled: Boolean,
            neteaseProviderTrackId: String
        ): LyricsDocument {
            documentResult?.let {
                events += "document:${track.id}:$onlineEnabled:$neteaseProviderTrackId"
                return it
            }
            return super.loadDocumentForTrack(track, onlineEnabled, neteaseProviderTrackId)
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 10_000L, Uri.EMPTY, "file:$id.mp3")
}
