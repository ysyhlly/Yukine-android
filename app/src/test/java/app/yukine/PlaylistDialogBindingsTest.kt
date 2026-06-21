package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDialogBindingsTest {
    @Test
    fun forwardsPlaylistDialogActionsToBoundOperations() {
        val calls = mutableListOf<String>()
        val bindings = PlaylistDialogBindings(
            createPlaylistAction = PlaylistNameAction { calls += "create:$it" },
            renamePlaylistAction = PlaylistRenameAction { playlistId, name ->
                calls += "rename:$playlistId:$name"
            },
            deletePlaylistAction = PlaylistDeleteAction { playlistId, name ->
                calls += "delete:$playlistId:$name"
            },
            addTrackToPlaylistAction = PlaylistTrackAddAction { playlistId, trackId ->
                calls += "add:$playlistId:$trackId"
            }
        )

        bindings.createPlaylist("Daily")
        bindings.renamePlaylist(7L, "Renamed")
        bindings.deletePlaylist(8L, "Old")
        bindings.addTrackToPlaylist(9L, 10L)

        assertEquals(
            listOf(
                "create:Daily",
                "rename:7:Renamed",
                "delete:8:Old",
                "add:9:10"
            ),
            calls
        )
    }
}
