package app.yukine.playback.manager

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.model.TrackPlayRecord
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.ArrayList
import java.util.concurrent.Executor

@OptIn(UnstableApi::class)
internal class PlaybackMediaLibraryCallback(
    private val dataSource: DataSource,
    private val queryExecutor: Executor = Executor { command -> command.run() }
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
        return asyncQuery {
            val item = itemForAutoMediaId(mediaId)
            if (item == null) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(item, null)
            }
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
        return asyncQuery {
            val children = childrenForAutoParent(parentId, page, pageSize)
            if (children == null) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
            } else {
                LibraryResult.ofItemList(children, params)
            }
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return asyncQuery {
            val resolved = ArrayList<MediaItem>()
            for (track in tracksForMediaItems(mediaItems)) {
                resolved.add(mediaItemForTrack(track))
            }
            resolved
        }
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return asyncQuery {
            val resolvedTracks = resolvedTracksForMediaItems(mediaItems)
            val resolved = ArrayList<MediaItem>()
            for (resolvedTrack in resolvedTracks) {
                resolved.add(mediaItemForTrack(resolvedTrack.track))
            }
            val resolvedStartIndex = if (resolved.isEmpty()) {
                resolved.addAll(mediaItems)
                maxOf(0, minOf(startIndex, maxOf(resolved.size - 1, 0)))
            } else {
                remappedControllerStartIndex(resolvedTracks, startIndex)
            }
            MediaSession.MediaItemsWithStartPosition(
                resolved,
                resolvedStartIndex,
                startPositionMs
            )
        }
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
                if (TrackIdentity.isUsable(trackId)) {
                    val track = dataSource.loadCachedTracks().firstOrNull { it.id == trackId }
                    if (track != null) autoMediaItemForTrack(track) else null
                } else {
                    null
                }
            }
        }
    }

    private fun childrenForAutoParent(parentId: String?): List<MediaItem>? {
        return childrenForAutoParent(parentId, -1, -1)
    }

    private fun childrenForAutoParent(parentId: String?, page: Int, pageSize: Int): List<MediaItem>? {
        if (parentId == null) {
            return null
        }
        return when {
            AUTO_ROOT == parentId -> pagedItems(
                arrayListOf(
                    browsableItem(AUTO_ALL, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                    browsableItem(AUTO_RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                    browsableItem(AUTO_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    browsableItem(AUTO_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    browsableItem(AUTO_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                ),
                page,
                pageSize
            )
            AUTO_ALL == parentId -> pagedAutoItemsForTracks(dataSource.loadCachedTracks(), page, pageSize)
            AUTO_RECENT == parentId -> pagedAutoItemsForTracks(
                dataSource.loadRecentlyPlayed(100).mapNotNull { it.track },
                page,
                pageSize
            )
            AUTO_PLAYLISTS == parentId -> {
                val playlists = ArrayList<MediaItem>()
                for (playlist in pagedItems(dataSource.loadPlaylists(), page, pageSize)) {
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
                if (playlistId < 0L) {
                    null
                } else {
                    pagedAutoItemsForTracks(dataSource.loadPlaylistTracks(playlistId), page, pageSize)
                }
            }
            AUTO_ARTISTS == parentId -> groupedAutoItems(
                AUTO_ARTIST_PREFIX,
                MediaMetadata.MEDIA_TYPE_ARTIST,
                true,
                page,
                pageSize
            )
            parentId.startsWith(AUTO_ARTIST_PREFIX) -> {
                val artist = parentId.substring(AUTO_ARTIST_PREFIX.length)
                pagedAutoItemsForTracks(dataSource.loadCachedTracks(), page, pageSize) { it.artist == artist }
            }
            AUTO_ALBUMS == parentId -> groupedAutoItems(
                AUTO_ALBUM_PREFIX,
                MediaMetadata.MEDIA_TYPE_ALBUM,
                false,
                page,
                pageSize
            )
            parentId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val album = parentId.substring(AUTO_ALBUM_PREFIX.length)
                pagedAutoItemsForTracks(dataSource.loadCachedTracks(), page, pageSize) { it.album == album }
            }
            else -> null
        }
    }

    private fun groupedAutoItems(
        prefix: String,
        mediaType: Int,
        groupByArtist: Boolean,
        page: Int,
        pageSize: Int
    ): List<MediaItem> {
        val groups = linkedSetOf<String>()
        for (track in dataSource.loadCachedTracks()) {
            val key = if (groupByArtist) track.artist else track.album
            groups.add(key)
        }
        val items = ArrayList<MediaItem>()
        for (key in pagedItems(groups.toList(), page, pageSize)) {
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

    private fun pagedAutoItemsForTracks(
        tracks: List<Track>?,
        page: Int,
        pageSize: Int,
        include: (Track) -> Boolean = { true }
    ): List<MediaItem> {
        if (tracks.isNullOrEmpty()) {
            return emptyList()
        }
        if (page < 0 || pageSize <= 0) {
            return autoItemsForTracks(tracks.filter(include))
        }
        val fromIndex = page.toLong() * pageSize.toLong()
        val toIndexExclusive = fromIndex + pageSize.toLong()
        val items = ArrayList<MediaItem>(pageSize)
        var playableIndex = 0L
        for (track in tracks) {
            if (!include(track) || !PlaybackMediaSourceProvider.hasPlayableMediaUri(track)) {
                continue
            }
            if (playableIndex >= toIndexExclusive) {
                break
            }
            if (playableIndex >= fromIndex) {
                items.add(autoMediaItemForTrack(track))
            }
            playableIndex++
        }
        return items
    }

    private data class ResolvedControllerTrack(
        val originalIndex: Int,
        val track: Track
    )

    private fun tracksForMediaItems(mediaItems: List<MediaItem>?): List<Track> =
        resolvedTracksForMediaItems(mediaItems).map { it.track }

    private fun resolvedTracksForMediaItems(mediaItems: List<MediaItem>?): List<ResolvedControllerTrack> {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return emptyList()
        }
        val tracksById = dataSource.loadCachedTracks().associateBy { it.id }
        val tracks = ArrayList<ResolvedControllerTrack>()
        for ((index, mediaItem) in mediaItems.withIndex()) {
            val track = trackForMediaItem(mediaItem, tracksById) ?: continue
            tracks.add(ResolvedControllerTrack(index, track))
        }
        return tracks
    }

    fun controllerQueueForMediaItems(
        mediaItems: List<MediaItem>?,
        startIndex: Int,
        startPositionMs: Long
    ): ControllerQueue? {
        val resolvedTracks = resolvedTracksForMediaItems(mediaItems)
        if (resolvedTracks.isEmpty()) {
            return null
        }
        val remappedStartIndex = remappedControllerStartIndex(resolvedTracks, startIndex)
        return ControllerQueue(resolvedTracks.map { it.track }, remappedStartIndex, startPositionMs)
    }

    private fun remappedControllerStartIndex(
        resolvedTracks: List<ResolvedControllerTrack>,
        requestedStartIndex: Int
    ): Int {
        if (requestedStartIndex < 0) {
            return 0
        }
        val exactMatch = resolvedTracks.indexOfFirst { it.originalIndex == requestedStartIndex }
        if (exactMatch >= 0) {
            return exactMatch
        }
        val firstAfterRequested = resolvedTracks.indexOfFirst { it.originalIndex > requestedStartIndex }
        if (firstAfterRequested >= 0) {
            return firstAfterRequested
        }
        return resolvedTracks.lastIndex
    }

    private fun trackForMediaItem(mediaItem: MediaItem?, tracksById: Map<Long, Track>): Track? {
        if (mediaItem == null) {
            return null
        }
        var trackId = trackIdFromAutoMediaId(mediaItem.mediaId)
        if (!TrackIdentity.isUsable(trackId)) {
            trackId = parseLong(mediaItem.mediaId, -1L)
        }
        return if (!TrackIdentity.isUsable(trackId)) null else tracksById[trackId]
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

    private fun <T> pagedItems(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (items.isEmpty()) {
            return emptyList()
        }
        if (page < 0 || pageSize <= 0) {
            return items
        }
        val fromIndex = page.toLong() * pageSize.toLong()
        if (fromIndex >= items.size.toLong()) {
            return emptyList()
        }
        val toIndex = minOf(items.size.toLong(), fromIndex + pageSize.toLong()).toInt()
        return items.subList(fromIndex.toInt(), toIndex)
    }

    private fun mediaItemForTrack(track: Track): MediaItem {
        return dataSource.mediaItemForTrack(track)
    }

    private fun <T> asyncQuery(query: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        queryExecutor.execute {
            try {
                future.set(query())
            } catch (error: Exception) {
                future.setException(error)
            }
        }
        return future
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
