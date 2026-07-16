package app.yukine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistSourceResolverTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("streaming_playlist_sync", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("streaming_playlist_sync", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistedProviderLinkWinsAfterStoreIsRecreated() {
        StreamingPlaylistSyncStore(context).linkPlaylist(
            localPlaylistId = 41L,
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = "qq-list-41"
        )

        val restoredProvider = StreamingPlaylistSyncStore(context).getLink(41L)?.provider
        val source = PlaylistSourceResolver.resolve(
            restoredProvider,
            listOf(streamingTrack(1L, StreamingProviderName.LUOXUE))
        )

        assertEquals(StreamingProviderName.QQ_MUSIC, source)
    }

    @Test
    fun legacyImportedPlaylistUsesItsTrackProvider() {
        val source = PlaylistSourceResolver.resolve(
            linkedProvider = null,
            tracks = listOf(
                streamingTrack(1L, StreamingProviderName.NETEASE),
                streamingTrack(2L, StreamingProviderName.NETEASE)
            )
        )

        assertEquals(StreamingProviderName.NETEASE, source)
    }

    @Test
    fun mixedOrLocalPlaylistRemainsInLocalFolder() {
        assertNull(
            PlaylistSourceResolver.resolve(
                linkedProvider = null,
                tracks = listOf(
                    streamingTrack(1L, StreamingProviderName.NETEASE),
                    localTrack(2L)
                )
            )
        )
        assertNull(
            PlaylistSourceResolver.resolve(
                linkedProvider = null,
                tracks = listOf(
                    streamingTrack(3L, StreamingProviderName.NETEASE),
                    streamingTrack(4L, StreamingProviderName.LUOXUE)
                )
            )
        )
    }

    private fun streamingTrack(id: Long, provider: StreamingProviderName): Track = Track(
        id,
        "Track $id",
        "Artist",
        "Album",
        120_000L,
        Uri.EMPTY,
        "streaming:${provider.wireName}:track-$id"
    )

    private fun localTrack(id: Long): Track = Track(
        id,
        "Track $id",
        "Artist",
        "Album",
        120_000L,
        Uri.EMPTY,
        "C:/Music/track-$id.flac"
    )
}
