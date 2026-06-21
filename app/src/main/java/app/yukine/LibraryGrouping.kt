package app.yukine

import android.net.Uri
import app.yukine.model.Track
import java.io.File
import java.util.ArrayList
import java.util.LinkedHashMap

internal object LibraryGrouping {
    @JvmField val HOME: String = "home"
    @JvmField val SONGS: String = "songs"
    @JvmField val ALBUMS: String = "albums"
    @JvmField val ARTISTS: String = "artists"
    @JvmField val FOLDERS: String = "folders"
    @JvmField val PLAYLISTS: String = "playlists"

    @JvmStatic
    fun groupTracks(tracks: List<Track>, mode: String): LinkedHashMap<String, ArrayList<Track>> {
        val grouped = LinkedHashMap<String, ArrayList<Track>>()
        for (track in tracks) {
            val key = groupKey(track, mode)
            val groupTracks = grouped.getOrPut(key) { ArrayList() }
            groupTracks.add(track)
        }
        val groups = ArrayList<Group>()
        for ((key, value) in grouped) {
            groups.add(Group(key, groupTitle(key, mode), value))
        }
        groups.sortWith { left, right ->
            val titleCompare = left.title.compareTo(right.title, ignoreCase = true)
            if (titleCompare != 0) titleCompare else right.tracks.size.compareTo(left.tracks.size)
        }
        val sorted = LinkedHashMap<String, ArrayList<Track>>()
        for (group in groups) {
            sorted[group.key] = group.tracks
        }
        return sorted
    }

    @JvmStatic
    fun groupTitle(key: String, mode: String): String {
        if (ALBUMS == mode) {
            val separator = key.indexOf('\u001f')
            val album = if (separator >= 0) key.substring(0, separator) else key
            return if (album.isEmpty()) "未知专辑" else album
        }
        if (FOLDERS == mode) {
            if (key.isEmpty()) {
                return "未知文件夹"
            }
            val name = File(key).name
            return if (name == null || name.isEmpty()) key else name
        }
        return if (key.isEmpty()) "未知" else key
    }

    @JvmStatic
    fun groupSubtitle(tracks: List<Track>, mode: String): String {
        if (tracks.isEmpty()) {
            return trackCountLabel(0)
        }
        val count = trackCountLabel(tracks.size)
        if (ALBUMS == mode) {
            return tracks[0].artist + " - " + count
        }
        if (ARTISTS == mode) {
            return albumCountLabel(albumCount(tracks)) + " - " + count
        }
        if (FOLDERS == mode) {
            val folder = folderKey(tracks[0])
            return if (folder.isEmpty()) count else "$folder - $count"
        }
        return count
    }

    @JvmStatic
    fun groupArtworkUri(tracks: List<Track>, mode: String): Uri? {
        if (ALBUMS != mode && ARTISTS != mode) {
            return null
        }
        return tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    @JvmStatic
    fun modeTitle(mode: String): String {
        if (HOME == mode) {
            return "首页"
        }
        if (ALBUMS == mode) {
            return "专辑"
        }
        if (ARTISTS == mode) {
            return "艺人"
        }
        if (FOLDERS == mode) {
            return "文件夹"
        }
        if (PLAYLISTS == mode) {
            return "歌单"
        }
        return "歌曲"
    }

    @JvmStatic
    fun uniqueAlbumCount(tracks: List<Track>): Int {
        val albums = HashSet<String>()
        for (track in tracks) {
            albums.add(track.album + "\u001f" + track.artist)
        }
        return albums.size
    }

    @JvmStatic
    fun uniqueArtistCount(tracks: List<Track>): Int {
        val artists = HashSet<String>()
        for (track in tracks) {
            artists.add(track.artist)
        }
        return artists.size
    }

    @JvmStatic
    fun uniqueFolderCount(tracks: List<Track>): Int {
        val folders = HashSet<String>()
        for (track in tracks) {
            folders.add(folderKey(track))
        }
        folders.remove("")
        return folders.size
    }

    @JvmStatic
    fun folderKey(track: Track): String {
        if (track.dataPath.isEmpty()) {
            return ""
        }
        val parent = File(track.dataPath).parentFile
        return parent?.absolutePath ?: ""
    }

    private fun groupKey(track: Track, mode: String): String {
        if (ALBUMS == mode) {
            return track.album + "\u001f" + track.artist
        }
        if (ARTISTS == mode) {
            return track.artist
        }
        if (FOLDERS == mode) {
            return folderKey(track)
        }
        return track.title
    }

    @JvmStatic
    fun albumCount(tracks: List<Track>): Int {
        val albums = HashSet<String>()
        for (track in tracks) {
            albums.add(track.album)
        }
        return albums.size
    }

    private fun trackCountLabel(count: Int): String {
        return "$count 首歌曲"
    }

    private fun albumCountLabel(count: Int): String {
        return "$count 张专辑"
    }

    private class Group(
        val key: String,
        val title: String,
        val tracks: ArrayList<Track>
    )
}
