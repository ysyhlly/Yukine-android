package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTrackMergePolicyTest {
    @Test
    fun mergesEquivalentLocalTracksWithNormalizedMetadataAndCloseDuration() {
        val result = LibraryTrackMergePolicy.merge(
            listOf(
                track(
                    id = 1L,
                    title = "メタフィクション",
                    artist = "Luna / ねんね",
                    album = "Bed Time Story",
                    durationMs = 259_000L,
                    dataPath = "/Music/meta-fiction.flac"
                ),
                track(
                    id = 2L,
                    title = "メタフィクション",
                    artist = "luna / ねんね",
                    album = "Bed Time Story",
                    durationMs = 261_500L,
                    dataPath = "document:content://provider/meta-fiction.mp3"
                )
            )
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun mergesTranslatedAliasesAcrossMetadataAndFeaturedSuffixes() {
        val tracks = listOf(
            track(
                1L,
                "9を眺めた魚達",
                "9を眺めた魚達 957",
                "9を眺めた魚達",
                240_000L,
                "/Music/nine-fish.flac"
            ),
            track(
                2L,
                "9を眺めた魚達",
                "9を眺めた魚達 (眺望9的鱼们) 957",
                "9を眺めた魚達",
                240_800L,
                "document:content://provider/nine-fish.mp3"
            ),
            track(
                3L,
                "ラピダリー",
                "*Luna / ゆある",
                "ラピダリー",
                266_000L,
                "/Music/lapidary.flac"
            ),
            track(
                4L,
                "ラピダリー (feat. Yuaru)",
                "*Luna / ゆある",
                "ラピダリー",
                266_500L,
                "stream:https://example.test/lapidary.mp3"
            ),
            track(
                5L,
                "ラピダリー",
                "*Luna / ゆある",
                "ラピダリー (feat. Yuaru)",
                265_700L,
                "webdav:https://example.test/lapidary-hires.flac"
            )
        )

        val snapshot = LibraryTrackMergePolicy.snapshot(tracks)

        assertEquals(listOf(1L, 3L), snapshot.mergedTracks.map { it.id })
        assertEquals(listOf(1L, 2L), snapshot.sourceCandidatesByTrackId[1L]?.map { it.id })
        assertEquals(listOf(3L, 4L, 5L), snapshot.sourceCandidatesByTrackId[3L]?.map { it.id })
    }

    @Test
    fun mergesEquivalentSourcesButKeepsDifferentVersionsAndIncompleteMetadataSeparate() {
        val tracks = listOf(
                track(1L, "Echo", "Artist", "Album", 180_000L, "/Music/echo.flac"),
                track(2L, "Echo (Remix)", "Artist", "Album", 180_000L, "/Music/echo-remix.flac"),
                track(3L, "Echo", "Artist", "Another Album", 180_000L, "/Music/echo-other.flac"),
                track(4L, "Echo", "Artist", "Album", 184_001L, "/Music/echo-live.flac"),
                track(5L, "未知歌曲", "Artist", "Album", 180_000L, "/Music/unknown.flac"),
                track(6L, "Echo", "Artist", "Album", 180_000L, "stream:https://example.test/echo.mp3"),
                track(7L, "Echo", "Artist", "Album", 180_000L, "stream:https://example.test/echo-2.mp3"),
                track(8L, "Echo (现场)", "Artist", "Album", 180_000L, "/Music/echo-live-cn.flac"),
                track(9L, "Echo (リミックス)", "Artist", "Album", 180_000L, "/Music/echo-remix-jp.flac"),
                track(10L, "Echo (Live)", "Artist", "Album", 180_000L, "/Music/echo-live.flac"),
                track(11L, "Echo (2023 Ver.)", "Artist", "Album", 180_000L, "/Music/echo-ver.flac"),
                track(12L, "Echo (混音)", "Artist", "Album", 180_000L, "/Music/echo-mix-cn.flac"),
                track(13L, "Echo (夏)", "Artist", "Album", 180_000L, "/Music/echo-summer.flac"),
                track(14L, "Echo (魚達)", "Artist", "Album", 180_000L, "/Music/echo-fish.flac")
            )
        val result = LibraryTrackMergePolicy.merge(tracks)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 8L, 9L, 10L, 11L, 12L, 13L, 14L), result.map { it.id })
        assertEquals(
            listOf(1L, 6L, 7L),
            LibraryTrackMergePolicy.sourceCandidatesFor(result.first(), tracks).map { it.id }
        )
    }

    @Test
    fun mainLibraryStoreUsesMergedTracksForTheLibraryAndSearchResults() {
        var searchCalls = 0
        val store = MainLibraryStore(
            LibrarySearchUseCase(
                object : LibrarySearchOperations {
                    override fun search(source: List<Track>, query: String?): List<Track> {
                        searchCalls++
                        val normalizedQuery = query.orEmpty().trim()
                        return if (normalizedQuery.isEmpty()) {
                            source
                        } else {
                            source.filter { track ->
                                track.title.contains(normalizedQuery, ignoreCase = true)
                            }
                        }
                    }
                }
            ),
            MainActivityViewModel(SavedStateHandle())
        )
        store.replaceLibrary(
            listOf(
                track(1L, "NPC", "Luna / ねんね", "Bed Time Story", 250_000L, "/Music/npc.flac"),
                track(2L, "NPC", "Luna / ねんね", "Bed Time Story", 250_500L, "document:content://provider/npc.mp3"),
                track(3L, "Remote", "Artist", "Album", 180_000L, "stream:https://example.test/remote.mp3")
            ),
            emptySet(),
            null
        )

        assertEquals(listOf(1L, 3L), store.allTracks().map { it.id })
        assertEquals(listOf(1L, 3L), store.visibleTracks().map { it.id })
        assertEquals(0, searchCalls)
        assertEquals(listOf(1L, 2L), store.sourceCandidatesFor(track(1L, "NPC", "Luna / ねんね", "Bed Time Story", 250_000L, "/Music/npc.flac")).map { it.id })

        store.applySearch("npc")

        assertEquals(listOf(1L), store.visibleTracks().map { it.id })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        dataPath: String
    ): Track = Track(id, title, artist, album, durationMs, Uri.EMPTY, dataPath)
}
