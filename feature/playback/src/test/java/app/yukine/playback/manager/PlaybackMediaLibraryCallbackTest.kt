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
        override fun appName(): String = "Yukine"

        override fun loadCachedTracks(): List<Track> = cachedTracks

        override fun loadPlaylists(): List<Playlist> = emptyList()

        override fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord> = emptyList()

        override fun loadPlaylistTracks(playlistId: Long): List<Track> = emptyList()

        override fun mediaItemForTrack(track: Track): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .build()
            return PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, metadata)
        }
    }
}
