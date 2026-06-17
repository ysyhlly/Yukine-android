package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGatewayBindingsTest {
    @Test
    fun delegatesLibraryGatewayCallsToBindings() {
        val calls = mutableListOf<String>()
        val gateway = LibraryGatewayBindings(
            playTrackListAction = LibraryTrackListAction { tracks, index ->
                calls += "play:${tracks.size}:$index:${tracks.first().title}"
            },
            statusTextProvider = LibraryStatusTextProvider { key -> "text:$key" },
            statusSink = LibraryStatusSink { message -> calls += "status:$message" },
            favoriteAction = LibraryFavoriteAction { trackId, favorite ->
                calls += "favorite:$trackId:$favorite"
            },
            addToPlaylistAction = LibraryTrackAction { track ->
                calls += "add:${track.title}"
            },
            changeGroupModeAction = LibraryModeAction { mode -> calls += "mode:$mode" },
            openGroupAction = LibraryGroupAction { key, title -> calls += "group:$key:$title" },
            openPlaylistAction = LibraryPlaylistAction { playlistId, title ->
                calls += "playlist:$playlistId:$title"
            },
            backFromGroupAction = Runnable { calls += "back" },
            searchAction = LibrarySearchAction { query -> calls += "search:$query" },
            importFilesAction = Runnable { calls += "import" },
            scanLibraryAction = Runnable { calls += "scan" }
        )
        val track = Track(
            7L,
            "Song",
            "Artist",
            "Album",
            123000L,
            Uri.parse("content://media/external/audio/media/7"),
            "/music/song.mp3"
        )

        gateway.playTrackList(listOf(track), 2)
        gateway.showStatusKey("library.ready")
        gateway.applyFavorite(9L, true)
        gateway.addToPlaylist(track)
        gateway.changeGroupMode("albums")
        gateway.openGroup("group-1", "Group 1")
        gateway.openPlaylist(42L, "Playlist 42")
        gateway.backFromGroup()
        gateway.search("echo")
        gateway.importFiles()
        gateway.scanLibrary()

        assertEquals(
            listOf(
                "play:1:2:Song",
                "status:text:library.ready",
                "favorite:9:true",
                "add:Song",
                "mode:albums",
                "group:group-1:Group 1",
                "playlist:42:Playlist 42",
                "back",
                "search:echo",
                "import",
                "scan"
            ),
            calls
        )
    }
}
