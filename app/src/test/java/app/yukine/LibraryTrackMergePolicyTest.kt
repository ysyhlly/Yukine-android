@file:Suppress("DEPRECATION")

package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [LibraryTrackMergePolicy].
 *
 * Note: Several tests use deprecated V4 metadata clustering methods ([LibraryTrackMergePolicy.merge],
 * [LibraryTrackMergePolicy.snapshot]) which are retained for backward compatibility.
 * Production code should use [LibraryTrackMergePolicy.persistedRecordingSnapshot].
 */
class LibraryTrackMergePolicyTest {
    @Test
    fun artistGroupingUsesStableArtistIdAndIncludesFeaturedCredit() {
        val first = track(91L, "One", "Hanser feat. Guest", "Album", 180_000L, "/one.flac")
        val second = track(92L, "Two", "hanser", "Album", 181_000L, "/two.flac")
        val identities = mapOf(
            91L to listOf(
                LibraryArtistGroupIdentity("artist-hanser", "Hanser"),
                LibraryArtistGroupIdentity("artist-guest", "Guest")
            ),
            92L to listOf(LibraryArtistGroupIdentity("artist-hanser", "Hanser"))
        )

        val groups = LibraryGrouping.groupTracks(listOf(first, second), LibraryGrouping.ARTISTS) {
            identities[it.id].orEmpty()
        }

        assertEquals(2, groups.size)
        assertEquals(listOf(91L, 92L), groups.getValue("artist:artist-hanser\u001fHanser").map { it.id })
        assertEquals(listOf(91L), groups.getValue("artist:artist-guest\u001fGuest").map { it.id })
        assertEquals("Hanser", LibraryGrouping.groupTitle("artist:artist-hanser\u001fHanser", LibraryGrouping.ARTISTS))
    }

    @Test
    fun canonicalClusterPrefersLocalRepresentativeAndKeepsWebDavAlternative() {
        val webDav = track(81L, "Song", "Artist", "Album", 180_000L, "webdav:1:/song.flac")
        val local = track(82L, "Song", "Artist", "Album", 180_000L, "/music/song.flac")

        val snapshot = LibraryTrackMergePolicy.snapshot(listOf(webDav, local)) { "recording:1" }

        assertEquals(listOf(82L), snapshot.mergedTracks.map { it.id })
        assertEquals(listOf(82L, 81L), snapshot.sourceCandidatesByTrackId.getValue(local.id).map { it.id })
        assertEquals(listOf(82L, 81L), snapshot.sourceCandidatesByTrackId.getValue(webDav.id).map { it.id })
    }

    @Test
    fun persistedSnapshotDoesNotFuzzyMergeUnpersistedRows() {
        val local = track(83L, "Song", "Artist", "Album", 180_000L, "/music/song.flac")
        val webDav = track(84L, "Song", "Artist", "Album", 180_000L, "webdav:1:/song.flac")

        val snapshot = LibraryTrackMergePolicy.persistedSnapshot(listOf(local, webDav)) { null }

        assertEquals(listOf(83L, 84L), snapshot.mergedTracks.map { it.id })
        assertEquals(emptyMap<Long, List<Track>>(), snapshot.sourceCandidatesByTrackId)
    }

    @Test
    fun persistedRecordingSnapshotUsesLongIdentityAndKeepsVersionsSeparate() {
        val original = track(85L, "Song", "Artist", "Album", 180_000L, "/music/song.flac")
        val webDav = track(86L, "Song alias", "Artist", "Remote", 180_500L, "webdav:1:/song.flac")
        val live = track(87L, "Song (Live)", "Artist", "Tour", 196_000L, "webdav:1:/song-live.flac")

        val snapshot = LibraryTrackMergePolicy.persistedRecordingSnapshot(
            listOf(original, webDav, live)
        ) { track -> if (track.id == live.id) 902L else 901L }

        assertEquals(listOf(85L, 87L), snapshot.mergedTracks.map { it.id })
        assertEquals(listOf(85L, 86L), snapshot.sourceCandidatesByTrackId.getValue(85L).map { it.id })
    }

    @Test
    fun fixedIdentityAcceptanceDatasetKeepsOriginalLiveRemixCoverAndSameTitleDifferentArtistSeparate() {
        val tracks = listOf(
            track(101L, "Baseline Song", "Baseline Artist", "Local Album", 180_000L, "/Music/baseline.flac"),
            track(102L, "Baseline Song", "Baseline Artist", "DAV Album", 180_500L, "webdav:1:baseline.flac"),
            track(103L, "Baseline Song", "Baseline Artist", "网易专辑", 179_800L, "streaming:netease:103"),
            track(104L, "Baseline Song", "Baseline Artist", "QQ 专辑", 180_100L, "streaming:qqmusic:104"),
            track(105L, "Baseline Song", "Baseline Artist", "LX", 180_200L, "streaming:luoxue:wy:105"),
            track(106L, "Baseline Song (Live)", "Baseline Artist", "Tour", 196_000L, "streaming:luoxue:tx:106"),
            track(107L, "Baseline Song (Remix)", "Baseline Artist", "Remixes", 182_000L, "streaming:netease:107"),
            track(108L, "Baseline Song", "Cover Artist", "Covers", 180_000L, "streaming:qqmusic:108"),
            track(109L, "Baseline Song", "Different Artist", "Same-title songs", 180_000L, "webdav:1:same-title.flac")
        )
        val original = "canonical:11111111-1111-1111-1111-111111111111"
        val live = "canonical:22222222-2222-2222-2222-222222222222"
        val remix = "canonical:33333333-3333-3333-3333-333333333333"
        val cover = "canonical:44444444-4444-4444-4444-444444444444"
        val sameTitleDifferentArtist = "canonical:55555555-5555-5555-5555-555555555555"

        val snapshot = LibraryTrackMergePolicy.snapshot(tracks) { track ->
            when (track.id) {
                106L -> live
                107L -> remix
                108L -> cover
                109L -> sameTitleDifferentArtist
                else -> original
            }
        }

        assertEquals(listOf(101L, 106L, 107L, 108L, 109L), snapshot.mergedTracks.map { it.id })
        assertEquals(
            listOf(101L, 102L, 103L, 104L, 105L),
            snapshot.sourceCandidatesByTrackId.getValue(101L).map { it.id }
        )
    }

    @Test
    fun lxCanonicalIdentityMergesMetadataAliasesButKeepsDifferentVersionsSeparate() {
        val tracks = listOf(
            track(1L, "Anytime Anywhere", "milet", "葬送のフリーレン", 211_000L, "webdav:1:anytime.flac"),
            track(2L, "Anytime Anywhere (随时随地)", "milet", "Streaming", 210_200L, "streaming:netease:1"),
            track(3L, "Anytime Anywhere", "milet", "Live at Budokan", 211_000L, "streaming:luoxue:tx:live")
        )
        val anchors = mapOf(1L to "luoxue:wy:main", 2L to "luoxue:wy:main", 3L to "luoxue:tx:live")

        val snapshot = LibraryTrackMergePolicy.snapshot(tracks) { anchors[it.id] }

        assertEquals(listOf(1L, 3L), snapshot.mergedTracks.map { it.id })
        assertEquals(listOf(1L, 2L), snapshot.sourceCandidatesByTrackId.getValue(1L).map { it.id })
    }

    @Test
    fun metadataOnlyLocalTracksRemainSeparateWithoutPersistedIdentity() {
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

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun metadataOnlyRowsDoNotChainAcrossDurationNeighbors() {
        val tracks = listOf(
            track(1L, "Echo", "Artist A / Artist B", "Album", 180_000L, "/Music/echo.flac"),
            track(2L, "Echo", "Artist B feat. Artist A", "Album", 182_500L, "document:echo.mp3"),
            track(3L, "Echo", "Artist A & Artist B", "Album", 185_000L, "/Music/echo-copy.flac")
        )

        val snapshot = LibraryTrackMergePolicy.snapshot(tracks)

        assertEquals(listOf(1L, 2L, 3L), snapshot.mergedTracks.map { it.id })
        assertEquals(emptyMap<Long, List<Track>>(), snapshot.sourceCandidatesByTrackId)
    }

    @Test
    fun metadataAliasesRemainSeparateUntilCanonicalIdentityIsPersisted() {
        val tracks = listOf(
            track(
                1L,
                "10年後の私になら",
                "こはならむ",
                "10年後の私になら",
                244_672L,
                "webdav:1:https://nas.test/10-years.flac"
            ),
            track(
                2L,
                "10年後の私になら",
                "こはならむ",
                "10年後の私になら",
                244_672L,
                "streaming:netease:1965921366"
            ),
            track(
                3L,
                "10年後の私になら (如果是10年后的我)",
                "こはならむ (Kohana Lam)",
                "Streaming",
                244_000L,
                "streaming:qqmusic:003YZunC32e9SG"
            )
        )

        val snapshot = LibraryTrackMergePolicy.snapshot(tracks)

        assertEquals(listOf(1L, 2L, 3L), snapshot.mergedTracks.map { it.id })
        assertEquals(emptyMap<Long, List<Track>>(), snapshot.sourceCandidatesByTrackId)
    }

    @Test
    fun translatedMetadataAliasesRequireCanonicalIdentityBeforeMerging() {
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

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), snapshot.mergedTracks.map { it.id })
        assertEquals(emptyMap<Long, List<Track>>(), snapshot.sourceCandidatesByTrackId)
    }

    @Test
    fun metadataOnlyRowsRemainSeparateEvenWhenAlbumAndDurationAreCompatible() {
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

        assertEquals((1L..14L).toList(), result.map { it.id })
        assertEquals(emptyMap<Long, List<Track>>(), LibraryTrackMergePolicy.sourceCandidateIndex(tracks))
    }

    @Test
    fun libraryDataOwnerUsesPersistedRecordingIdentitiesForLibraryAndSearchResults() {
        val store = LibraryViewModel().dataOwner()
        // Unified dedup: use recordingIdentitySnapshotProvider (production path)
        store.bindRecordingIdentitySnapshotProvider {
            mapOf(1L to 100L, 2L to 100L) // tracks 1 and 2 share recording 100
        }
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
