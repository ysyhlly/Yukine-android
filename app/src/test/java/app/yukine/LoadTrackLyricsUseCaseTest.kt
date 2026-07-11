package app.yukine

import android.net.Uri
import app.yukine.data.LyricsRepository
import app.yukine.model.LyricsLine
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

    private class FakeTrackLyricsOperations : TrackLyricsOperations {
        val events = mutableListOf<String>()
        var result: List<LyricsLine> = emptyList()

        override suspend fun loadForTrack(
            track: Track,
            onlineEnabled: Boolean,
            neteaseProviderTrackId: String
        ): List<LyricsLine> {
            events += "load:${track.id}:$onlineEnabled:$neteaseProviderTrackId"
            return result
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 10_000L, Uri.EMPTY, "file:$id.mp3")
}
