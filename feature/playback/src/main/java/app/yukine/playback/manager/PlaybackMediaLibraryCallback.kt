package app.yukine.playback.manager

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.ArrayList

internal class PlaybackMediaLibraryCallback(
    private val dataSource: DataSource
) : MediaLibrarySession.Callback {
    data class ControllerQueue(
        val tracks: List<Track>,
        val startIndex: Int,
        val startPositionMs: Long
    )

    interface DataSource {
        fun appName(): String
        fun loadCachedTracks(): List<Track>
        fun loadPlaylists(): List<Playlist>
        fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord>
        fun loadPlaylistTracks(playlistId: Long): List<Track>
        fun mediaItemForTrack(track: Track): MediaItem
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                browsableItem(AUTO_ROOT, dataSource.appName(), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                params
            )
        )
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = itemForAutoMediaId(mediaId)
        return if (item == null) {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        } else {
            Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val children = childrenForAutoParent(parentId)
        return if (children == null) {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params))
        } else {
            Futures.immediateFuture(LibraryResult.ofItemList(pagedItems(children, page, pageSize), params))
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val resolved = ArrayList<MediaItem>()
        for (track in tracksForMediaItems(mediaItems)) {
            resolved.add(mediaItemForTrack(track))
        }
        return Futures.immediateFuture(resolved)
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val resolved = ArrayList<MediaItem>()
        for (track in tracksForMediaItems(mediaItems)) {
            resolved.add(mediaItemForTrack(track))
        }
        if (resolved.isEmpty()) {
            resolved.addAll(mediaItems)
        }
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(
                resolved,
                maxOf(0, minOf(startIndex, maxOf(resolved.size - 1, 0))),
                startPositionMs
            )
        )
    }

    private fun itemForAutoMediaId(mediaId: String?): MediaItem? {
        if (mediaId == null) {
            return null
        }
        return when {
            AUTO_ROOT == mediaId -> browsableItem(AUTO_ROOT, dataSource.appName(), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            AUTO_ALL == mediaId -> browsableItem(AUTO_ALL, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            AUTO_RECENT == mediaId -> browsableItem(AUTO_RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            AUTO_PLAYLISTS == mediaId -> browsableItem(AUTO_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            AUTO_ARTISTS == mediaId -> browsableItem(AUTO_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
            AUTO_ALBUMS == mediaId -> browsableItem(AUTO_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            mediaId.startsWith(AUTO_PLAYLIST_PREFIX) -> {
                val playlistId = parseLong(mediaId.substring(AUTO_PLAYLIST_PREFIX.length), -1L)
                dataSource.loadPlaylists().firstOrNull { it.id == playlistId }?.let {
                    browsableItem(mediaId, it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                }
            }
            mediaId.startsWith(AUTO_ARTIST_PREFIX) -> browsableItem(
                mediaId,
                mediaId.substring(AUTO_ARTIST_PREFIX.length),
                MediaMetadata.MEDIA_TYPE_ARTIST
            )
            mediaId.startsWith(AUTO_ALBUM_PREFIX) -> browsableItem(
                mediaId,
                mediaId.substring(AUTO_ALBUM_PREFIX.length),
                MediaMetadata.MEDIA_TYPE_ALBUM
            )
            else -> {
                val trackId = trackIdFromAutoMediaId(mediaId)
                if (trackId >= 0L) {
                    val track = dataSource.loadCachedTracks().firstOrNull { it.id == trackId }
                    if (track != null) autoMediaItemForTrack(track) else null
                } else {
                    null
                }
            }
        }
    }

    private fun childrenForAutoParent(parentId: String?): List<MediaItem>? {
        if (parentId == null) {
            return null
        }
        return when {
            AUTO_ROOT == parentId -> arrayListOf(
                browsableItem(AUTO_ALL, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                browsableItem(AUTO_RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                browsableItem(AUTO_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsableItem(AUTO_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                browsableItem(AUTO_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            )
            AUTO_ALL == parentId -> autoItemsForTracks(dataSource.loadCachedTracks())
            AUTO_RECENT == parentId -> autoItemsForTracks(
                dataSource.loadRecentlyPlayed(100).mapNotNull { it.track }
            )
            AUTO_PLAYLISTS == parentId -> {
                val playlists = ArrayList<MediaItem>()
                for (playlist in dataSource.loadPlaylists()) {
                    playlists.add(
                        browsableItem(
                            AUTO_PLAYLIST_PREFIX + playlist.id,
                            playlist.name,
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    )
                }
                playlists
            }
            parentId.startsWith(AUTO_PLAYLIST_PREFIX) -> {
                val playlistId = parseLong(parentId.substring(AUTO_PLAYLIST_PREFIX.length), -1L)
                if (playlistId < 0L) null else autoItemsForTracks(dataSource.loadPlaylistTracks(playlistId))
            }
            AUTO_ARTISTS == parentId -> groupedAutoItems(AUTO_ARTIST_PREFIX, MediaMetadata.MEDIA_TYPE_ARTIST, true)
            parentId.startsWith(AUTO_ARTIST_PREFIX) -> {
                val artist = parentId.substring(AUTO_ARTIST_PREFIX.length)
                autoItemsForTracks(dataSource.loadCachedTracks().filter { it.artist == artist })
            }
            AUTO_ALBUMS == parentId -> groupedAutoItems(AUTO_ALBUM_PREFIX, MediaMetadata.MEDIA_TYPE_ALBUM, false)
            parentId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val album = parentId.substring(AUTO_ALBUM_PREFIX.length)
                autoItemsForTracks(dataSource.loadCachedTracks().filter { it.album == album })
            }
            else -> null
        }
    }

    private fun groupedAutoItems(prefix: String, mediaType: Int, groupByArtist: Boolean): List<MediaItem> {
        val counts = linkedMapOf<String, Int>()
        for (track in dataSource.loadCachedTracks()) {
            val key = if (groupByArtist) track.artist else track.album
            counts[key] = (counts[key] ?: 0) + 1
        }
        val items = ArrayList<MediaItem>()
        for (key in counts.keys) {
            items.add(browsableItem(prefix + key, key, mediaType))
        }
        return items
    }

    private fun autoItemsForTracks(tracks: List<Track>?): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        if (tracks == null) {
            return items
        }
        for (track in tracks) {
            if (PlaybackMediaSourceProvider.hasPlayableMediaUri(track)) {
                items.add(autoMediaItemForTrack(track))
            }
        }
        return items
    }

    private fun tracksForMediaItems(mediaItems: List<MediaItem>?): List<Track> {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return emptyList()
        }
        val tracksById = dataSource.loadCachedTracks().associateBy { it.id }
        val tracks = ArrayList<Track>()
        for (mediaItem in mediaItems) {
            trackForMediaItem(mediaItem, tracksById)?.let { tracks.add(it) }
        }
        return tracks
    }

    fun controllerQueueForMediaItems(
        mediaItems: List<MediaItem>?,
        startIndex: Int,
        startPositionMs: Long
    ): ControllerQueue? {
        val tracks = tracksForMediaItems(mediaItems)
        if (tracks.isEmpty()) {
            return null
        }
        return ControllerQueue(tracks, startIndex, startPositionMs)
    }

    private fun trackForMediaItem(mediaItem: MediaItem?, tracksById: Map<Long, Track>): Track? {
        if (mediaItem == null) {
            return null
        }
        var trackId = trackIdFromAutoMediaId(mediaItem.mediaId)
        if (trackId < 0L) {
            trackId = parseLong(mediaItem.mediaId, -1L)
        }
        return if (trackId < 0L) null else tracksById[trackId]
    }

    private fun trackIdFromAutoMediaId(mediaId: String?): Long {
        if (mediaId == null || !mediaId.startsWith(AUTO_TRACK_PREFIX)) {
            return -1L
        }
        return parseLong(mediaId.substring(AUTO_TRACK_PREFIX.length), -1L)
    }

    private fun parseLong(value: String?, fallback: Long): Long {
        if (value == null || value.isEmpty()) {
            return fallback
        }
        return try {
            value.toLong()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    private fun autoMediaItemForTrack(track: Track): MediaItem {
        val playableItem = mediaItemForTrack(track)
        val metadata = MediaMetadata.Builder()
            .populate(playableItem.mediaMetadata)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        return playableItem
            .buildUpon()
            .setMediaId(AUTO_TRACK_PREFIX + track.id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun browsableItem(mediaId: String, title: String, mediaType: Int): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    private fun pagedItems(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (items.isEmpty()) {
            return emptyList()
        }
        if (page < 0 || pageSize <= 0) {
            return items
        }
        val fromIndex = page * pageSize
        if (fromIndex >= items.size) {
            return emptyList()
        }
        val toIndex = minOf(items.size, fromIndex + pageSize)
        return items.subList(fromIndex, toIndex)
    }

    private fun mediaItemForTrack(track: Track): MediaItem {
        return dataSource.mediaItemForTrack(track)
    }

    companion object {
        private const val AUTO_ROOT = "echo:auto:root"
        private const val AUTO_ALL = "echo:auto:all"
        private const val AUTO_RECENT = "echo:auto:recent"
        private const val AUTO_PLAYLISTS = "echo:auto:playlists"
        private const val AUTO_PLAYLIST_PREFIX = "echo:auto:playlist:"
        private const val AUTO_ARTISTS = "echo:auto:artists"
        private const val AUTO_ARTIST_PREFIX = "echo:auto:artist:"
        private const val AUTO_ALBUMS = "echo:auto:albums"
        private const val AUTO_ALBUM_PREFIX = "echo:auto:album:"
        private const val AUTO_TRACK_PREFIX = "echo:auto:track:"
    }
}
