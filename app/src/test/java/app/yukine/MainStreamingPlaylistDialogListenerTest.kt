package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStreamingPlaylistDialogListenerTest {
    @Test
    fun delegatesDialogCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val tracks = listOf(dialogTrack(1L), dialogTrack(2L))
        val playlists = listOf(StreamingPlaylist(StreamingProviderName.NETEASE, "100", "Daily", trackCount = 12))
        val listener = listener(calls)

        listener.setStatus("No providers")
        listener.runStreamingPlaylistImport(StreamingProviderName.QQ_MUSIC, "Favorites", tracks)
        listener.importSelectedAccountPlaylists(StreamingProviderName.NETEASE, playlists)
        listener.importStreamingLikedTracks(StreamingProviderName.KUGOU)

        assertEquals(
            listOf(
                "status:No providers",
                "playlist:qqmusic:Favorites:2",
                "account:netease:100",
                "liked:kugou"
            ),
            calls
        )
    }

    @Test
    fun directConstructionCreatesStreamingPlaylistDialogControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainStreamingPlaylistDialogListener(
            StreamingPlaylistDialogStatusSink { calls += "status:$it" },
            StreamingPlaylistImportRunner { provider, playlistName, tracks ->
                calls += "playlist:${provider.wireName}:$playlistName:${tracks.size}"
            },
            AccountPlaylistImportSink { provider, playlists ->
                calls += "account:${provider.wireName}:${playlists.size}"
            },
            StreamingLikedTracksImportSink { provider -> calls += "liked:${provider.wireName}" }
        )

        listener.setStatus("Ready")
        listener.runStreamingPlaylistImport(StreamingProviderName.NETEASE, "Local", listOf(dialogTrack(3L)))
        listener.importSelectedAccountPlaylists(
            StreamingProviderName.QQ_MUSIC,
            listOf(StreamingPlaylist(StreamingProviderName.QQ_MUSIC, "200", "Cloud"))
        )
        listener.importStreamingLikedTracks(StreamingProviderName.KUGOU)

        assertEquals(
            listOf("status:Ready", "playlist:netease:Local:1", "account:qqmusic:1", "liked:kugou"),
            calls
        )
    }

    private fun listener(calls: MutableList<String>): StreamingPlaylistDialogController.Listener =
        MainStreamingPlaylistDialogListener(
            statusSink = StreamingPlaylistDialogStatusSink { calls += "status:$it" },
            playlistImportRunner = StreamingPlaylistImportRunner { provider, playlistName, tracks ->
                calls += "playlist:${provider.wireName}:$playlistName:${tracks.size}"
            },
            accountPlaylistImportSink = AccountPlaylistImportSink { provider, playlists ->
                calls += "account:${provider.wireName}:${playlists.single().providerPlaylistId}"
            },
            likedTracksImportSink = StreamingLikedTracksImportSink { provider -> calls += "liked:${provider.wireName}" }
        )
}

private fun dialogTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
