package app.yukine.streaming

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourcePolicyTest {
    @Test
    fun defaultSnapshotEnablesBilibiliPlayback() {
        val snapshot = PlaybackSourcePolicySnapshot()

        assertTrue(snapshot.isEnabled(StreamingProviderName.BILIBILI))
        assertTrue(StreamingProviderName.BILIBILI in snapshot.remotePriority)
    }

    @Test
    fun disabledProviderDoesNotResolveOrUseCachedUrl() = runTest {
        val provider = RecordingProvider(StreamingProviderName.NETEASE)
        val policy = TestPolicy(setOf(StreamingProviderName.LUOXUE))
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider))),
            playbackSourcePolicy = policy
        )

        val result = runCatching { repository.resolvePlayback(StreamingProviderName.NETEASE, "42") }
        assertTrue(result.isFailure)
        assertEquals(0, provider.resolveCount)
    }

    @Test
    fun enabledProviderEntersCandidateAndSyncCapabilitiesRemainEnabled() = runTest {
        val provider = RecordingProvider(StreamingProviderName.NETEASE)
        val policy = TestPolicy(setOf(StreamingProviderName.LUOXUE, StreamingProviderName.NETEASE))
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider))),
            playbackSourcePolicy = policy
        )

        repository.resolvePlayback(StreamingProviderName.NETEASE, "42")
        val capability = repository.providerCapabilities().single()
        assertEquals(1, provider.resolveCount)
        assertTrue(capability.supportsAudioResolve)
        assertTrue(capability.supportsFavoritesRead)
        assertTrue(capability.supportsPlaylistReadSync)
    }

    @Test
    fun qqIsMetadataOnlyEvenWhenPolicyAndDescriptorClaimPlayback() = runTest {
        val provider = RecordingProvider(StreamingProviderName.QQ_MUSIC)
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider))),
            playbackSourcePolicy = TestPolicy(setOf(StreamingProviderName.QQ_MUSIC, StreamingProviderName.LUOXUE))
        )
        val result = runCatching { repository.resolvePlayback(StreamingProviderName.QQ_MUSIC, "qq-1") }
        assertTrue(result.isFailure)
        assertEquals(0, provider.resolveCount)
        assertFalse(repository.providerCapabilities().single().supportsAudioResolve)
        assertTrue(repository.providerCapabilities().single().supportsFavoritesRead)
        assertTrue(repository.providerCapabilities().single().supportsPlaylistReadSync)
    }

    @Test
    fun qqMetadataResolvesThroughLuoxueWithoutRequestingQqAudio() = runTest {
        val qq = RecordingProvider(StreamingProviderName.QQ_MUSIC)
        val luoxue = RecordingProvider(StreamingProviderName.LUOXUE, searchTrackId = "lx-1")
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(qq, luoxue))),
            playbackSourcePolicy = TestPolicy(setOf(StreamingProviderName.LUOXUE))
        )
        val metadata = track(StreamingProviderName.QQ_MUSIC, "qq-1")

        val result = repository.resolvePlaybackTrack(metadata.provider, metadata.providerTrackId, metadata = metadata)

        assertEquals(StreamingProviderName.LUOXUE, result.source.provider)
        assertEquals(1, luoxue.resolveCount)
        assertEquals(0, qq.resolveCount)
    }

    @Test
    fun qqMetadataRetriesTransientLuoxueTitleSearchTimeout() = runTest {
        val qq = RecordingProvider(StreamingProviderName.QQ_MUSIC)
        val luoxue = RecordingProvider(
            StreamingProviderName.LUOXUE,
            searchTrackId = "lx-after-timeout",
            searchDelaysMs = listOf(200L, 0L)
        )
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(qq, luoxue))),
            playbackSourcePolicy = TestPolicy(setOf(StreamingProviderName.LUOXUE)),
            titleSearchTimeoutMs = 100L
        )
        val metadata = track(StreamingProviderName.QQ_MUSIC, "qq-1")

        val result = repository.resolvePlaybackTrack(
            metadata.provider,
            metadata.providerTrackId,
            metadata = metadata
        )

        assertEquals(StreamingProviderName.LUOXUE, result.source.provider)
        assertEquals(2, luoxue.searchCount)
        assertEquals(1, luoxue.resolveCount)
        assertEquals(0, qq.resolveCount)
    }

    @Test
    fun luoxueFailureNeverFallsBackToQq() = runTest {
        val qq = RecordingProvider(StreamingProviderName.QQ_MUSIC, searchTrackId = "qq-fallback")
        val luoxue = RecordingProvider(StreamingProviderName.LUOXUE, searchTrackId = "lx-1", failResolve = true)
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(qq, luoxue))),
            playbackSourcePolicy = TestPolicy(setOf(StreamingProviderName.LUOXUE, StreamingProviderName.QQ_MUSIC))
        )
        val metadata = track(StreamingProviderName.QQ_MUSIC, "qq-1")

        val result = runCatching {
            repository.resolvePlaybackTrack(metadata.provider, metadata.providerTrackId, metadata = metadata)
        }

        assertTrue(result.isFailure)
        assertEquals(1, luoxue.resolveCount)
        assertEquals(0, qq.resolveCount)
    }

    private class TestPolicy(private val enabled: Set<StreamingProviderName>) : PlaybackSourcePolicy {
        override fun snapshot() = PlaybackSourcePolicySnapshot(enabled, listOf(StreamingProviderName.LUOXUE) + enabled)
    }

    private class RecordingProvider(
        private val name: StreamingProviderName,
        private val searchTrackId: String? = null,
        private val failResolve: Boolean = false,
        private val searchDelaysMs: List<Long> = emptyList()
    ) : StreamingProvider {
        var searchCount = 0
        var resolveCount = 0
        override val descriptor = StreamingProviderDescriptor(
            name,
            name.wireName,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsFavorites = true,
                supportsPlaylists = true,
                supportsFavoritesRead = true,
                supportsFavoritesWrite = true,
                supportsPlaylistReadSync = true
            )
        )

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
            val attempt = searchCount++
            delay(searchDelaysMs.getOrElse(attempt) { 0L })
            return StreamingSearchResult(
                request.provider,
                request.query,
                request.page,
                request.pageSize,
                tracks = searchTrackId?.let { listOf(track(name, it)) }.orEmpty()
            )
        }
        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
            resolveCount += 1
            if (failResolve) throw StreamingGatewayException("unavailable", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
            return StreamingPlaybackSource(name, request.providerTrackId, "https://audio.example/${request.providerTrackId}.flac")
        }
        override suspend fun authState() = StreamingAuthState()
    }

    private companion object {
        fun track(provider: StreamingProviderName, id: String) = StreamingTrack(
            provider = provider,
            providerTrackId = id,
            title = "夜空",
            artist = "歌手",
            album = "专辑",
            durationMs = 180_000L
        )
    }
}
