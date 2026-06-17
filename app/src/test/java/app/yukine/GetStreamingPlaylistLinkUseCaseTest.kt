package app.yukine

import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetStreamingPlaylistLinkUseCaseTest {
    @Test
    fun loadsLinkForValidPlaylist() {
        val operations = FakeStreamingPlaylistLinkOperations()
        operations.link = link(12L)

        val result = GetStreamingPlaylistLinkUseCase(operations).execute(12L)

        assertEquals(12L, result?.localPlaylistId)
        assertEquals(listOf("get:12"), operations.events)
    }

    @Test
    fun ignoresInvalidPlaylistId() {
        val operations = FakeStreamingPlaylistLinkOperations()

        val result = GetStreamingPlaylistLinkUseCase(operations).execute(-1L)

        assertNull(result)
        assertEquals(emptyList<String>(), operations.events)
    }

    private class FakeStreamingPlaylistLinkOperations : StreamingPlaylistLinkOperations {
        var link: StreamingPlaylistSyncStore.LinkedPlaylist? = null
        val events = mutableListOf<String>()

        override fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? {
            events.add("get:$localPlaylistId")
            return link
        }
    }

    private fun link(playlistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist =
        StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = playlistId,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-$playlistId",
            lastSyncMs = 0L
        )
}
