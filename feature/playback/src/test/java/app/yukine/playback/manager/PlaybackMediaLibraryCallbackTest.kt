package app.yukine.playback.manager

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackMediaLibraryCallbackTest {
    @Test
    fun rootChildrenExposeBrowsableLibrarySections() {
        val callback = PlaybackMediaLibraryCallback(FakeDataSource())

        val children = childrenForAutoParent(callback, "echo:auto:root")

        assertEquals(
            listOf(
                "echo:auto:all",
                "echo:auto:recent",
                "echo:auto:playlists",
                "echo:auto:artists",
                "echo:auto:albums"
            ),
            children?.map { it.mediaId }
        )
        assertTrue(children.orEmpty().all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun autoTrackMediaIdRestoresPlayableMediaItem() {
        val callback = PlaybackMediaLibraryCallback(
            FakeDataSource(listOf(track(7L, Uri.parse("file:///music/seven.flac"))))
        )

        val item = itemForAutoMediaId(callback, "echo:auto:track:7")

        assertNotNull(item)
        assertEquals("echo:auto:track:7", item?.mediaId)
        assertEquals("Track 7", item?.mediaMetadata?.title.toString())
        assertTrue(item?.mediaMetadata?.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, item?.mediaMetadata?.mediaType)
    }

    @Test
    fun autoItemsForTracksUsesMediaSourcePlayableUriRule() {
        val callback = PlaybackMediaLibraryCallback(FakeDataSource())
        val items = autoItemsForTracks(
            callback,
            listOf(
                track(1L, Uri.parse("file:///music/local.flac")),
                track(2L, Uri.EMPTY)
            )
        )

        assertEquals(listOf("echo:auto:track:1"), items.map { it.mediaId })
        assertEquals("Track 1", items.first().mediaMetadata.title.toString())
    }

    @Test
    fun allSongsPagingConvertsOnlyRequestedPlayableTracks() {
        val dataSource = FakeDataSource(
            (1L..100L).map { id ->
                track(id, if (id % 10L == 0L) Uri.EMPTY else Uri.parse("file:///music/$id.flac"))
            }
        )
        val callback = PlaybackMediaLibraryCallback(dataSource)

        val items = childrenForAutoParent(callback, "echo:auto:all", page = 2, pageSize = 20)

        assertEquals(20, items?.size)
        assertEquals("echo:auto:track:45", items?.first()?.mediaId)
        assertEquals("echo:auto:track:66", items?.last()?.mediaId)
        assertEquals(20, dataSource.mediaItemConversions)
    }

    @Test
    fun controllerQueueForMediaItemsResolvesPlainAndAutoTrackIds() {
        val callback = PlaybackMediaLibraryCallback(
            FakeDataSource(
                listOf(
                    track(1L, Uri.parse("file:///music/one.flac")),
                    track(2L, Uri.parse("file:///music/two.flac"))
                )
            )
        )

        val queue = callback.controllerQueueForMediaItems(
            listOf(
                MediaItem.Builder().setMediaId("1").build(),
                MediaItem.Builder().setMediaId("echo:auto:track:2").build()
            ),
            startIndex = 1,
            startPositionMs = 2400L
        )

        assertEquals(listOf(1L, 2L), queue?.tracks?.map { it.id })
        assertEquals(1, queue?.startIndex)
        assertEquals(2400L, queue?.startPositionMs)
    }

    @Test
    fun controllerQueueForMediaItemsRemapsStartIndexAfterFilteringUnresolvedItems() {
        val callback = PlaybackMediaLibraryCallback(
            FakeDataSource(
                listOf(
                    track(1L, Uri.parse("file:///music/one.flac")),
                    track(3L, Uri.parse("file:///music/three.flac")),
                    track(4L, Uri.parse("file:///music/four.flac"))
                )
            )
        )

        val queue = callback.controllerQueueForMediaItems(
            listOf(
                MediaItem.Builder().setMediaId("1").build(),
                MediaItem.Builder().setMediaId("missing").build(),
                MediaItem.Builder().setMediaId("echo:auto:track:3").build(),
                MediaItem.Builder().setMediaId("4").build()
            ),
            startIndex = 2,
            startPositionMs = 3600L
        )

        assertEquals(listOf(1L, 3L, 4L), queue?.tracks?.map { it.id })
        assertEquals(1, queue?.startIndex)
        assertEquals(3600L, queue?.startPositionMs)
    }

    @Test
    fun controllerQueueForMediaItemsFallsForwardOrBackWhenRequestedStartItemIsFiltered() {
        val callback = PlaybackMediaLibraryCallback(
            FakeDataSource(
                listOf(
                    track(1L, Uri.parse("file:///music/one.flac")),
                    track(3L, Uri.parse("file:///music/three.flac"))
                )
            )
        )
        val mediaItems = listOf(
            MediaItem.Builder().setMediaId("1").build(),
            MediaItem.Builder().setMediaId("missing").build(),
            MediaItem.Builder().setMediaId("echo:auto:track:3").build()
        )

        val fallsForward = callback.controllerQueueForMediaItems(mediaItems, startIndex = 1, startPositionMs = 0L)
        val fallsBack = callback.controllerQueueForMediaItems(mediaItems, startIndex = 99, startPositionMs = 0L)
        val negativeClampsToFirst = callback.controllerQueueForMediaItems(mediaItems, startIndex = -5, startPositionMs = 0L)

        assertEquals(listOf(1L, 3L), fallsForward?.tracks?.map { it.id })
        assertEquals(1, fallsForward?.startIndex)
        assertEquals(1, fallsBack?.startIndex)
        assertEquals(0, negativeClampsToFirst?.startIndex)
    }

    @Test
    fun controllerQueueForMediaItemsReturnsNullWhenNothingResolves() {
        val callback = PlaybackMediaLibraryCallback(
            FakeDataSource(listOf(track(1L, Uri.parse("file:///music/one.flac"))))
        )

        val queue = callback.controllerQueueForMediaItems(
            listOf(MediaItem.Builder().setMediaId("missing").build()),
            startIndex = 0,
            startPositionMs = 0L
        )

        assertNull(queue)
    }

    private fun itemForAutoMediaId(
        callback: PlaybackMediaLibraryCallback,
        mediaId: String
    ): MediaItem? {
        val method = PlaybackMediaLibraryCallback::class.java
            .getDeclaredMethod("itemForAutoMediaId", String::class.java)
        method.isAccessible = true
        return method.invoke(callback, mediaId) as MediaItem?
    }

    @Suppress("UNCHECKED_CAST")
    private fun childrenForAutoParent(
        callback: PlaybackMediaLibraryCallback,
        parentId: String
    ): List<MediaItem>? {
        val method = PlaybackMediaLibraryCallback::class.java
            .getDeclaredMethod("childrenForAutoParent", String::class.java)
        method.isAccessible = true
        return method.invoke(callback, parentId) as List<MediaItem>?
    }

    @Suppress("UNCHECKED_CAST")
    private fun childrenForAutoParent(
        callback: PlaybackMediaLibraryCallback,
        parentId: String,
        page: Int,
        pageSize: Int
    ): List<MediaItem>? {
        val method = PlaybackMediaLibraryCallback::class.java.getDeclaredMethod(
            "childrenForAutoParent",
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(callback, parentId, page, pageSize) as List<MediaItem>?
    }

    @Suppress("UNCHECKED_CAST")
    private fun autoItemsForTracks(
        callback: PlaybackMediaLibraryCallback,
        tracks: List<Track>
    ): List<MediaItem> {
        val method = PlaybackMediaLibraryCallback::class.java
            .getDeclaredMethod("autoItemsForTracks", List::class.java)
        method.isAccessible = true
        return method.invoke(callback, tracks) as List<MediaItem>
    }

    private fun track(id: Long, uri: Uri): Track {
        return Track(
            id,
            "Track $id",
            "Artist",
            "Album",
            180_000L,
            uri,
            "local:$id"
        )
    }

    private class FakeDataSource(
        private val cachedTracks: List<Track> = emptyList()
    ) : PlaybackMediaLibraryCallback.DataSource {
        var mediaItemConversions: Int = 0

        override fun appName(): String = "Yukine"

        override fun loadCachedTracks(): List<Track> = cachedTracks

        override fun loadPlaylists(): List<Playlist> = emptyList()

        override fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord> = emptyList()

        override fun loadPlaylistTracks(playlistId: Long): List<Track> = emptyList()

        override fun mediaItemForTrack(track: Track): MediaItem {
            mediaItemConversions++
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .build()
            return PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, metadata)
        }
    }
}
