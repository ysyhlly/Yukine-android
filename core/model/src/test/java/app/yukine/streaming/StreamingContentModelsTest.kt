package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingContentModelsTest {
    @Test
    fun trackStableKeyUsesProviderWireNameAndProviderTrackId() {
        val track = StreamingTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = "songMid|mediaMid",
            title = "Echo",
            artist = "Yukine"
        )

        assertEquals("streaming:qqmusic:songMid|mediaMid", track.stableKey)
    }

    @Test
    fun searchResultBuildsUnifiedItemsWhenExplicitItemsAreEmpty() {
        val track = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            title = "Track",
            artist = "Artist",
            album = "Album"
        )
        val album = StreamingAlbum(
            provider = StreamingProviderName.NETEASE,
            providerAlbumId = "album-1",
            title = "Album",
            artist = "Artist"
        )

        val result = StreamingSearchResult(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            page = 1,
            pageSize = 20,
            tracks = listOf(track),
            albums = listOf(album)
        )

        assertEquals(
            listOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM),
            result.unifiedItems.map { it.type }
        )
        assertEquals("track-1", result.unifiedItems.first().id)
        assertEquals("album-1", result.unifiedItems.last().id)
    }

    @Test
    fun diagnosticsCacheHitRateAvoidsDivideByZero() {
        assertEquals(0, StreamingGatewayDiagnostics().cacheHitRate)
        assertEquals(40, StreamingGatewayDiagnostics(totalRequests = 5, cacheHits = 2).cacheHitRate)
    }
}
