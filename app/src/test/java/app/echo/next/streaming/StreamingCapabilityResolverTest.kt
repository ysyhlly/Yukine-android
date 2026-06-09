package app.echo.next.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingCapabilityResolverTest {
    @Test
    fun enabledProviderExposesSupportedActions() {
        val descriptor = descriptor(
            supportsSearch = true,
            supportsPlayback = true,
            supportsAuth = true,
            supportsFavorites = true,
            supportsPlaylists = true
        )

        assertTrue(StreamingCapabilityResolver.canSearch(descriptor))
        assertTrue(StreamingCapabilityResolver.canPlayback(descriptor))
        assertTrue(StreamingCapabilityResolver.canAuth(descriptor))
        assertTrue(StreamingCapabilityResolver.canFavorites(descriptor))
        assertTrue(StreamingCapabilityResolver.canPlaylists(descriptor))
        assertEquals(
            listOf("search", "playback", "auth", "favorites", "playlists"),
            StreamingCapabilityResolver.actionLabels(descriptor)
        )
    }

    @Test
    fun disabledProviderDoesNotExposeActionsEvenWhenCapabilitiesAreTrue() {
        val descriptor = descriptor(
            enabled = false,
            supportsSearch = true,
            supportsPlayback = true,
            supportsAuth = true,
            supportsFavorites = true,
            supportsPlaylists = true
        )

        assertFalse(StreamingCapabilityResolver.canSearch(descriptor))
        assertFalse(StreamingCapabilityResolver.canPlayback(descriptor))
        assertFalse(StreamingCapabilityResolver.canAuth(descriptor))
        assertFalse(StreamingCapabilityResolver.canFavorites(descriptor))
        assertFalse(StreamingCapabilityResolver.canPlaylists(descriptor))
        assertEquals(emptyList<String>(), StreamingCapabilityResolver.actionLabels(descriptor))
    }

    @Test
    fun searchMediaTypesRespectPlaylistAndMvCapabilities() {
        val descriptor = descriptor(
            supportsSearch = true,
            supportsPlaylists = false,
            supportsMv = false,
            supportedMediaTypes = setOf(
                StreamingMediaType.TRACK,
                StreamingMediaType.PLAYLIST,
                StreamingMediaType.MV
            )
        )

        assertEquals(
            setOf(StreamingMediaType.TRACK),
            StreamingCapabilityResolver.supportedSearchMediaTypes(descriptor)
        )
    }

    @Test
    fun unsupportedOnlySearchMediaTypesDoNotFallbackToTrack() {
        val descriptor = descriptor(
            supportsSearch = true,
            supportsPlaylists = false,
            supportsMv = false,
            supportedMediaTypes = setOf(StreamingMediaType.PLAYLIST, StreamingMediaType.MV)
        )

        assertEquals(emptySet<StreamingMediaType>(), StreamingCapabilityResolver.supportedSearchMediaTypes(descriptor))
    }

    private fun descriptor(
        enabled: Boolean = true,
        supportsSearch: Boolean = false,
        supportsPlayback: Boolean = false,
        supportsAuth: Boolean = false,
        supportsFavorites: Boolean = false,
        supportsPlaylists: Boolean = false,
        supportsMv: Boolean = false,
        supportedMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK)
    ): StreamingProviderDescriptor {
        return StreamingProviderDescriptor(
            name = StreamingProviderName.SPOTIFY,
            displayName = "Spotify",
            enabled = enabled,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = supportsSearch,
                supportsPlayback = supportsPlayback,
                supportsAuth = supportsAuth,
                supportsFavorites = supportsFavorites,
                supportsPlaylists = supportsPlaylists,
                supportsMv = supportsMv,
                supportedMediaTypes = supportedMediaTypes
            )
        )
    }
}
