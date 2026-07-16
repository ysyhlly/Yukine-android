package app.yukine.streaming

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingProviderContractTest {
    @Test
    fun registryGatewayNormalizesSearchRequestsBeforeCallingProvider() = runTest {
        val provider = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.NETEASE,
                supportsSearch = true,
                supportsPlayback = true
            )
        )
        val gateway = RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider)))

        val result = gateway.search(
            StreamingSearchRequest(
                provider = StreamingProviderName.NETEASE,
                query = "  echo  ",
                mediaTypes = emptySet(),
                page = 0,
                pageSize = 500
            )
        )

        assertEquals("echo", result.query)
        assertEquals(1, result.page)
        assertEquals(50, result.pageSize)
        assertEquals(setOf(StreamingMediaType.TRACK), provider.searchRequests.single().mediaTypes)
    }

    @Test
    fun registryGatewayRejectsUnsupportedSearchAndPlaybackCapabilities() = runTest {
        val provider = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.QQ_MUSIC,
                supportsSearch = false,
                supportsPlayback = false
            )
        )
        val gateway = RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider)))

        val searchError = captureGatewayError {
            gateway.search(StreamingSearchRequest(StreamingProviderName.QQ_MUSIC, "echo"))
        }
        val playbackError = captureGatewayError {
            gateway.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "track-1"
                )
            )
        }

        assertTrue(searchError.message.orEmpty().contains("does not support search"))
        assertTrue(playbackError.message.orEmpty().contains("does not provide playback audio"))
        assertEquals(StreamingErrorCode.UNSUPPORTED_OPERATION, searchError.code)
        assertEquals(StreamingErrorCode.UNSUPPORTED_OPERATION, playbackError.code)
        assertEquals(0, provider.searchRequests.size)
        assertEquals(0, provider.playbackRequests.size)
    }

    @Test
    fun registryGatewayReportsMissingProviders() = runTest {
        val gateway = RegistryStreamingGateway(StreamingProviderRegistry())

        val error = captureGatewayError {
            gateway.authState(StreamingProviderName.SPOTIFY)
        }

        assertTrue(error.message.orEmpty().contains("is not registered"))
        assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, error.code)
    }

    @Test
    fun registryGatewayReportsProviderHealth() = runTest {
        val healthy = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.NETEASE,
                supportsSearch = true,
                supportsPlayback = true,
                auth = StreamingAuthState(connected = true)
            )
        )
        val disabled = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.KUGOU,
                supportsSearch = true,
                supportsPlayback = true
            ).copy(
                enabled = false,
                status = StreamingProviderStatus.ERROR,
                statusMessage = "offline"
            )
        )
        val gateway = RegistryStreamingGateway(StreamingProviderRegistry(listOf(healthy, disabled)))

        val health = gateway.providersHealth()

        assertEquals(true, health.first { it.provider == StreamingProviderName.NETEASE }.available)
        assertEquals(true, health.first { it.provider == StreamingProviderName.NETEASE }.authenticated)
        val failed = health.first { it.provider == StreamingProviderName.KUGOU }
        assertFalse(failed.available)
        assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, failed.errorCode)
        assertEquals("offline", failed.errorMessage)
    }

    @Test
    fun registryGatewayReportsProviderCapabilities() = runTest {
        val provider = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.SPOTIFY,
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = true
            )
        )
        val gateway = RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider)))

        val capability = gateway.providerCapabilities().single()

        assertEquals(StreamingProviderName.SPOTIFY, capability.provider)
        assertTrue(capability.supportsSearch)
        assertTrue(capability.supportsPlayback)
        assertTrue(capability.supportsAuth)
        assertEquals(setOf(StreamingMediaType.TRACK), capability.supportedSearchMediaTypes)
        assertEquals(listOf("search", "playback", "auth"), capability.actions)
    }

    @Test
    fun providerDefaultAuthOperationsReturnDescriptorAuthState() = runTest {
        val auth = StreamingAuthState(
            kind = StreamingAuthKind.REMOTE_GATEWAY,
            connected = false,
            statusMessage = "Needs gateway"
        )
        val provider = FakeProvider(
            descriptor = descriptor(
                name = StreamingProviderName.TIDAL,
                supportsSearch = true,
                supportsPlayback = true,
                supportsAuth = true,
                auth = auth
            )
        )

        val state = provider.authState()
        val started = provider.startAuth(StreamingAuthRequest(StreamingProviderName.TIDAL))
        val completed = provider.completeAuth("echo-next://streaming-auth?code=1")
        val signedOut = provider.signOut()

        assertFalse(state.connected)
        assertEquals(auth, started.state)
        assertEquals(auth, completed.state)
        assertEquals(auth, signedOut)
        assertEquals("Auth is not supported", started.statusMessage)
        assertEquals("Auth is not supported", completed.statusMessage)
    }

    private suspend fun captureGatewayError(block: suspend () -> Unit): StreamingGatewayException {
        return try {
            block()
            throw AssertionError("Expected StreamingGatewayException")
        } catch (error: StreamingGatewayException) {
            error
        }
    }

    private fun descriptor(
        name: StreamingProviderName,
        supportsSearch: Boolean,
        supportsPlayback: Boolean,
        supportsAuth: Boolean = false,
        auth: StreamingAuthState = StreamingAuthState()
    ): StreamingProviderDescriptor {
        return StreamingProviderDescriptor(
            name = name,
            displayName = name.wireName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = supportsSearch,
                supportsPlayback = supportsPlayback,
                supportsAuth = supportsAuth
            ),
            auth = auth
        )
    }

    private class FakeProvider(
        override val descriptor: StreamingProviderDescriptor
    ) : StreamingProvider {
        val searchRequests = mutableListOf<StreamingSearchRequest>()
        val playbackRequests = mutableListOf<StreamingPlaybackRequest>()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            searchRequests += request
            return StreamingSearchResult(
                provider = request.provider,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize
            )
        }

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            playbackRequests += request
            return StreamingPlaybackSource(
                provider = request.provider,
                providerTrackId = request.providerTrackId,
                url = "https://stream.example.test/${request.providerTrackId}.flac"
            )
        }
    }
}
