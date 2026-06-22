package app.yukine.streaming

import app.yukine.streaming.cache.StreamingAuthMetadataEntity
import app.yukine.streaming.cache.StreamingCacheDao
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import app.yukine.streaming.cache.StreamingPlaybackCacheEntity
import app.yukine.streaming.cache.StreamingPlaylistCacheEntity
import app.yukine.streaming.cache.StreamingSearchCacheEntity
import app.yukine.model.Track
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamingRepositoryTest {
    @Test
    fun emptyRepositoryFactoryProvidesOfflineMockProvider() = runTest {
        val repository = StreamingRepositoryFactory.empty()

        val providers = repository.providers()
        val search = repository.search(
            provider = StreamingProviderName.MOCK,
            query = "Echo",
            mediaTypes = setOf(StreamingMediaType.TRACK)
        )

        assertEquals(StreamingProviderName.MOCK, providers.single().name)
        assertEquals("Echo test", search.tracks.single().title)
    }

    @Test
    fun searchReturnsCachedResultWithoutCallingGateway() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 1_000L }
        val cached = searchResult("cached-track")
        cache.saveSearch(
            StreamingSearchRequest(
                provider = StreamingProviderName.NETEASE,
                query = " Echo ",
                mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM),
                page = 1,
                pageSize = 20
            ),
            StreamingGatewayJson.searchResultJson(cached),
            ttlMs = 60_000L
        )
        val gateway = FakeStreamingGateway(searchResult = searchResult("remote-track"))
        val repository = StreamingRepository(gateway, cache)

        val result = repository.search(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.ALBUM, StreamingMediaType.TRACK),
            page = 1,
            pageSize = 20
        )

        assertEquals(listOf("cached-track"), result.tracks.map { it.providerTrackId })
        assertTrue(result.cached)
        assertEquals(0, gateway.searchRequests.size)
    }

    @Test
    fun diagnosticsRecordGatewayCallsAndCacheHits() = runTest {
        var nowMs = 10_000L
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { nowMs }
        val gateway = FakeStreamingGateway(searchResult = searchResult("remote-track"))
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(searchTtlMs = 60_000L),
            clockMs = { nowMs }
        )

        repository.search(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.TRACK)
        )
        nowMs = 10_010L
        repository.search(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.TRACK)
        )

        val diagnostics = repository.diagnostics()
        assertEquals(2, diagnostics.totalRequests)
        assertEquals(1, diagnostics.cacheHits)
        assertEquals(50, diagnostics.cacheHitRate)
        assertTrue(diagnostics.recentLogs.first().cacheHit)
        assertEquals("search", diagnostics.recentLogs.first().operation)
        assertEquals(StreamingProviderName.NETEASE, diagnostics.recentLogs.first().provider)
        assertFalse(diagnostics.recentLogs.last().cacheHit)
    }

    @Test
    fun searchSavesGatewayResultWithNormalizedRequestKey() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 2_000L }
        val gateway = FakeStreamingGateway(searchResult = searchResult("remote-track"))
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(searchTtlMs = 77L)
        )

        val result = repository.search(
            provider = StreamingProviderName.QQ_MUSIC,
            query = "Echo",
            mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.PLAYLIST),
            page = 2,
            pageSize = 30
        )

        assertFalse(result.cached)
        assertEquals(listOf("remote-track"), result.tracks.map { it.providerTrackId })
        assertEquals(
            StreamingSearchRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                query = "Echo",
                mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.PLAYLIST),
                page = 2,
                pageSize = 30
            ),
            gateway.searchRequests.single()
        )
        assertNotNull(
            dao.search("qqmusic", "qqmusic:echo:playlist,track:2:30", 2_000L)
        )
        assertEquals(
            2_077L,
            dao.searches["qqmusic" to "qqmusic:echo:playlist,track:2:30"]?.expiresAtMs
        )
    }

    @Test
    fun resolvePlaybackUsesAndWritesCache() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 3_000L }
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("remote-url")
        )
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(defaultPlaybackTtlMs = 88L),
            clockMs = { 10_000L }
        )

        val first = repository.resolvePlayback(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.LOSSLESS
        )
        val second = repository.resolvePlayback(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.LOSSLESS
        )

        assertEquals("remote-url", first.url)
        assertEquals("remote-url", second.url)
        assertEquals(1, gateway.playbackRequests.size)
        assertEquals(
            3_088L,
            dao.playbacks[Triple("netease", "track-1", "lossless")]?.expiresAtMs
        )
    }

    @Test
    fun playlistUsesAndWritesCache() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 5_000L }
        val gateway = FakeStreamingGateway(
            playlistDetail = playlistDetail("remote-track")
        )
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(playlistTtlMs = 123L)
        )

        val first = repository.playlist(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-1"
        )
        val second = repository.playlist(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-1"
        )

        assertFalse(first.cached)
        assertTrue(second.cached)
        assertEquals(listOf("remote-track"), second.tracks.map { it.providerTrackId })
        assertEquals(1, gateway.playlistRequests.size)
        assertEquals(5_123L, dao.playlists["netease" to "playlist-1"]?.expiresAtMs)
    }

    @Test
    fun resolvePlaybackUsesSourceExpiryForCacheTtlWhenPresent() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 10_000L }
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("expiring-url", expiresAtEpochMs = 15_000L)
        )
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(defaultPlaybackTtlMs = 88L),
            clockMs = { 10_000L }
        )

        repository.resolvePlayback(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.LOSSLESS
        )

        assertEquals(
            15_000L,
            dao.playbacks[Triple("netease", "track-1", "lossless")]?.expiresAtMs
        )
    }

    @Test
    fun resolvePlaybackTrackUsesInjectedPlaybackAdapter() = runTest {
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/track-1.m3u8")
        )
        val adapter = FakePlaybackTrackAdapter()
        val repository = StreamingRepository(
            gateway = gateway,
            playbackTrackAdapter = adapter
        )
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            title = "Echo",
            artist = "Tester"
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.HIGH,
            metadata = metadata
        )

        assertEquals("https://stream.example.test/track-1.m3u8", result.source.url)
        assertEquals("adapter-track-1", result.track.title)
        assertEquals(listOf("track-1"), adapter.sourceTrackIds)
        assertEquals(listOf(metadata), adapter.metadataRequests)
    }

    @Test
    fun resolvePlaybackTrackRecordsPreflightDiagnostics() = runTest {
        var nowMs = 1_000L
        val server = PreflightTestServer()
        server.start()
        try {
            val source = playbackSource("${server.baseUrl}/track-1.flac")
            val gateway = FakeStreamingGateway(
                playbackSource = source
            )
            val repository = StreamingRepository(
                gateway = gateway,
                playbackTrackAdapter = object : StreamingPlaybackTrackAdapter {
                    override fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack?): Track {
                        return Track(1L, "Echo", "Tester", "Album", 0L, android.net.Uri.EMPTY, "streaming:netease:track-1")
                    }
                },
                clockMs = {
                    nowMs += 25L
                    nowMs
                }
            )

            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "track-1",
                quality = StreamingAudioQuality.HIGH,
                metadata = null
            )

            val diagnostics = repository.diagnostics()
            val preflight = diagnostics.recentLogs.first { it.operation == "playback_preflight" }
            assertEquals(StreamingProviderName.NETEASE, preflight.provider)
            assertTrue(preflight.message.orEmpty().contains("http=204"))
            assertTrue(preflight.message.orEmpty().contains("host=127.0.0.1"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun resolvePlaybackTrackRejectsInvalidPlaybackSourceBeforeAdapter() = runTest {
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("")
        )
        val adapter = FakePlaybackTrackAdapter()
        val repository = StreamingRepository(
            gateway = gateway,
            playbackTrackAdapter = adapter
        )

        try {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "bad-track",
                quality = StreamingAudioQuality.HIGH,
                metadata = null
            )
            fail("Expected invalid playback source to fail")
        } catch (error: StreamingGatewayException) {
            assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, error.code)
        }

        assertTrue(adapter.sourceTrackIds.isEmpty())
    }

    @Test
    fun authStateUsesAndWritesCache() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 4_000L }
        val gateway = FakeStreamingGateway(
            authState = StreamingAuthState(
                kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                connected = true,
                accountDisplayName = "QQ User"
            )
        )
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(authMetadataTtlMs = 99L)
        )

        val first = repository.authState(StreamingProviderName.QQ_MUSIC)
        val second = repository.authState(StreamingProviderName.QQ_MUSIC)

        assertTrue(first.connected)
        assertEquals("QQ User", second.accountDisplayName)
        assertEquals(1, gateway.authStateProviders.size)
        assertEquals(4_099L, dao.auth["qqmusic"]?.expiresAtMs)
    }

    @Test
    fun disconnectedAuthCacheDoesNotHideFreshGatewayState() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 4_000L }
        cache.saveAuth(
            StreamingProviderName.NETEASE,
            StreamingGatewayJson.authStateJson(
                StreamingProviderName.NETEASE,
                StreamingAuthState(
                    kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                    connected = false
                )
            ),
            ttlMs = 60_000L
        )
        val gateway = FakeStreamingGateway(
            authState = StreamingAuthState(
                kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                connected = true,
                accountDisplayName = "Local User"
            )
        )
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            cachePolicy = StreamingCachePolicy(authMetadataTtlMs = 99L)
        )

        val state = repository.authState(StreamingProviderName.NETEASE)

        assertTrue(state.connected)
        assertEquals("Local User", state.accountDisplayName)
        assertEquals(1, gateway.authStateProviders.size)
    }

    @Test
    fun cacheRepositoryHonorsTtlAndPermanentAuthMetadata() = runTest {
        var nowMs = 10_000L
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { nowMs }
        val request = StreamingSearchRequest(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            page = 1,
            pageSize = 20
        )

        cache.saveSearch(request, """{"kind":"search"}""", ttlMs = 100L)
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-1",
            StreamingAudioQuality.LOSSLESS,
            """{"kind":"playback"}""",
            ttlMs = 100L
        )
        cache.savePlaylist(
            StreamingProviderName.NETEASE,
            "playlist-1",
            """{"kind":"playlist"}""",
            ttlMs = 100L
        )
        cache.saveAuth(StreamingProviderName.NETEASE, """{"kind":"auth"}""", ttlMs = null)

        assertEquals("""{"kind":"search"}""", cache.cachedSearch(request))
        assertEquals(
            """{"kind":"playback"}""",
            cache.cachedPlayback(StreamingProviderName.NETEASE, "track-1", StreamingAudioQuality.LOSSLESS)
        )
        assertEquals(
            """{"kind":"playlist"}""",
            cache.cachedPlaylist(StreamingProviderName.NETEASE, "playlist-1")
        )
        assertEquals("""{"kind":"auth"}""", cache.cachedAuth(StreamingProviderName.NETEASE))

        nowMs = 10_101L

        assertEquals(null, cache.cachedSearch(request))
        assertEquals(
            null,
            cache.cachedPlayback(StreamingProviderName.NETEASE, "track-1", StreamingAudioQuality.LOSSLESS)
        )
        assertEquals(null, cache.cachedPlaylist(StreamingProviderName.NETEASE, "playlist-1"))
        assertEquals("""{"kind":"auth"}""", cache.cachedAuth(StreamingProviderName.NETEASE))
        assertEquals(3, cache.clearExpired())
        assertEquals(0, cache.clearExpired())
    }

    @Test
    fun zeroTtlEntriesAreImmediatelyExpiredAndCleared() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 20_000L }
        val request = StreamingSearchRequest(
            provider = StreamingProviderName.QQ_MUSIC,
            query = "echo",
            page = 1,
            pageSize = 20
        )

        cache.saveSearch(request, """{"expired":true}""", ttlMs = 0L)
        cache.saveAuth(StreamingProviderName.QQ_MUSIC, """{"expired":true}""", ttlMs = 0L)

        assertEquals(null, cache.cachedSearch(request))
        assertEquals(null, cache.cachedAuth(StreamingProviderName.QQ_MUSIC))
        assertEquals(2, cache.clearExpired())
    }

    @Test
    fun cachedPlaybackBlockingReadsLatestUnexpiredPlaybackWithoutCoroutineBridge() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 40_000L }

        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-1",
            StreamingAudioQuality.HIGH,
            """{"quality":"high"}""",
            ttlMs = 100L
        )
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-1",
            StreamingAudioQuality.LOSSLESS,
            """{"quality":"lossless"}""",
            ttlMs = 50L
        )
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-2",
            StreamingAudioQuality.LOSSLESS,
            """{"quality":"other"}""",
            ttlMs = 100L
        )

        assertEquals(
            """{"quality":"lossless"}""",
            cache.cachedPlaybackBlocking(StreamingProviderName.NETEASE, "track-1")
        )
    }

    @Test
    fun clearExpiredCacheDelegatesToCacheRepository() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 30_000L }
        val repository = StreamingRepository(FakeStreamingGateway(), cache)
        val request = StreamingSearchRequest(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            page = 1,
            pageSize = 20
        )
        cache.saveSearch(request, """{"expired":true}""", ttlMs = 0L)
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-1",
            StreamingAudioQuality.LOSSLESS,
            """{"expired":true}""",
            ttlMs = 0L
        )
        cache.saveAuth(StreamingProviderName.NETEASE, """{"expired":true}""", ttlMs = 0L)

        assertEquals(3, repository.clearExpiredCache())
        assertEquals(0, repository.clearExpiredCache())
    }

    @Test
    fun providersHealthDelegatesToGateway() = runTest {
        val health = listOf(
            StreamingProviderHealth(
                provider = StreamingProviderName.SPOTIFY,
                available = true,
                authenticated = true,
                latencyMs = 50L
            )
        )
        val gateway = FakeStreamingGateway(health = health)
        val repository = StreamingRepository(gateway)

        assertEquals(health, repository.providersHealth())
        assertEquals(1, gateway.healthCalls)
    }

    @Test
    fun providerCapabilitiesDelegatesToGateway() = runTest {
        val capabilities = listOf(
            StreamingProviderCapability(
                provider = StreamingProviderName.SPOTIFY,
                displayName = "Spotify",
                enabled = true,
                supportsSearch = true,
                supportsPlayback = false,
                supportedSearchMediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM),
                actions = listOf("search")
            )
        )
        val gateway = FakeStreamingGateway(capabilities = capabilities)
        val repository = StreamingRepository(gateway)

        assertEquals(capabilities, repository.providerCapabilities())
        assertEquals(1, gateway.capabilityCalls)
    }
}

private class FakeStreamingGateway(
    private val searchResult: StreamingSearchResult = searchResult("track"),
    private val playlistDetail: StreamingPlaylistDetail = playlistDetail("playlist-track"),
    private val playbackSource: StreamingPlaybackSource = playbackSource("url"),
    private val authState: StreamingAuthState = StreamingAuthState(),
    private val health: List<StreamingProviderHealth> = emptyList(),
    private val capabilities: List<StreamingProviderCapability> = emptyList()
) : StreamingGateway {
    val searchRequests = mutableListOf<StreamingSearchRequest>()
    val playlistRequests = mutableListOf<StreamingPlaylistRequest>()
    val playbackRequests = mutableListOf<StreamingPlaybackRequest>()
    val authStateProviders = mutableListOf<StreamingProviderName>()
    var healthCalls = 0
    var capabilityCalls = 0

    override suspend fun providers(): List<StreamingProviderDescriptor> = emptyList()

    override suspend fun providerCapabilities(): List<StreamingProviderCapability> {
        capabilityCalls += 1
        return capabilities
    }

    override suspend fun providersHealth(): List<StreamingProviderHealth> {
        healthCalls += 1
        return health
    }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        searchRequests += request
        return searchResult.copy(
            provider = request.provider,
            query = request.query,
            page = request.page,
            pageSize = request.pageSize
        )
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        playlistRequests += request
        return playlistDetail.copy(
            provider = request.provider,
            providerPlaylistId = request.providerPlaylistId
        )
    }

    override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> {
        return emptyList()
    }

    override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> {
        return emptyList()
    }

    override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
        return emptyList()
    }

    override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
        return emptyList()
    }

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        playbackRequests += request
        return playbackSource.copy(
            provider = request.provider,
            providerTrackId = request.providerTrackId
        )
    }

    override suspend fun authState(provider: StreamingProviderName): StreamingAuthState {
        authStateProviders += provider
        return authState
    }

    override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
        return StreamingAuthResult(request.provider, authState)
    }

    override suspend fun completeAuth(
        provider: StreamingProviderName,
        callbackUri: String,
        cookieHeader: String?
    ): StreamingAuthResult {
        return StreamingAuthResult(provider, authState)
    }

    override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState = authState
}

private class PreflightTestServer {
    private val server = ServerSocket(0)
    private val ready = CountDownLatch(1)
    private lateinit var thread: Thread
    @Volatile
    private var running = false
    val baseUrl: String
        get() = "http://127.0.0.1:${server.localPort}"

    fun start() {
        running = true
        thread = Thread {
            ready.countDown()
            while (running && !server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().readLine()
                        socket.getOutputStream().write(
                            "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".toByteArray()
                        )
                        socket.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                    if (running) throw RuntimeException("Preflight test server failed")
                }
            }
        }
        thread.name = "StreamingPreflightTestServer"
        thread.isDaemon = true
        thread.start()
        ready.await(2, TimeUnit.SECONDS)
    }

    fun stop() {
        running = false
        server.close()
    }
}

private class FakePlaybackTrackAdapter : StreamingPlaybackTrackAdapter {
    val sourceTrackIds = mutableListOf<String>()
    val metadataRequests = mutableListOf<StreamingTrack?>()

    override fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack?): Track {
        sourceTrackIds += source.providerTrackId
        metadataRequests += metadata
        return Track(
            9001L,
            "adapter-${source.providerTrackId}",
            metadata?.artist ?: source.provider.wireName,
            metadata?.album ?: "Streaming",
            metadata?.durationMs ?: 0L,
            null,
            "streaming:${source.provider.wireName}:${source.providerTrackId}"
        )
    }
}

private class FakeStreamingCacheDao : StreamingCacheDao {
    val searches = mutableMapOf<Pair<String, String>, StreamingSearchCacheEntity>()
    val playlists = mutableMapOf<Pair<String, String>, StreamingPlaylistCacheEntity>()
    val playbacks = mutableMapOf<Triple<String, String, String>, StreamingPlaybackCacheEntity>()
    val auth = mutableMapOf<String, StreamingAuthMetadataEntity>()

    override suspend fun search(provider: String, cacheKey: String, nowMs: Long): StreamingSearchCacheEntity? {
        return searches[provider to cacheKey]?.takeIf { it.expiresAtMs > nowMs }
    }

    override suspend fun upsertSearch(entity: StreamingSearchCacheEntity) {
        searches[entity.provider to entity.cacheKey] = entity
    }

    override suspend fun playlist(
        provider: String,
        providerPlaylistId: String,
        nowMs: Long
    ): StreamingPlaylistCacheEntity? {
        return playlists[provider to providerPlaylistId]?.takeIf { it.expiresAtMs > nowMs }
    }

    override suspend fun upsertPlaylist(entity: StreamingPlaylistCacheEntity) {
        playlists[entity.provider to entity.providerPlaylistId] = entity
    }

    override suspend fun playback(
        provider: String,
        providerTrackId: String,
        quality: String,
        nowMs: Long
    ): StreamingPlaybackCacheEntity? {
        return playbacks[Triple(provider, providerTrackId, quality)]?.takeIf { it.expiresAtMs > nowMs }
    }

    override fun playbackBlocking(
        provider: String,
        providerTrackId: String,
        nowMs: Long
    ): StreamingPlaybackCacheEntity? {
        return playbacks.values
            .filter { it.provider == provider && it.providerTrackId == providerTrackId && it.expiresAtMs > nowMs }
            .sortedWith(
                compareBy<StreamingPlaybackCacheEntity> { playbackQualityRank(it.quality) }
                    .thenByDescending { it.expiresAtMs }
            )
            .firstOrNull()
    }

    override suspend fun upsertPlayback(entity: StreamingPlaybackCacheEntity) {
        playbacks[Triple(entity.provider, entity.providerTrackId, entity.quality)] = entity
    }

    override suspend fun auth(provider: String, nowMs: Long): StreamingAuthMetadataEntity? {
        return auth[provider]?.takeIf { it.expiresAtMs == null || it.expiresAtMs > nowMs }
    }

    override suspend fun upsertAuth(entity: StreamingAuthMetadataEntity) {
        auth[entity.provider] = entity
    }

    override suspend fun deleteExpiredSearch(nowMs: Long): Int {
        val expired = searches.filterValues { it.expiresAtMs <= nowMs }.keys.toList()
        expired.forEach { searches.remove(it) }
        return expired.size
    }

    override suspend fun deleteExpiredPlaylists(nowMs: Long): Int {
        val expired = playlists.filterValues { it.expiresAtMs <= nowMs }.keys.toList()
        expired.forEach { playlists.remove(it) }
        return expired.size
    }

    override suspend fun deleteExpiredPlayback(nowMs: Long): Int {
        val expired = playbacks.filterValues { it.expiresAtMs <= nowMs }.keys.toList()
        expired.forEach { playbacks.remove(it) }
        return expired.size
    }

    override suspend fun deleteExpiredAuth(nowMs: Long): Int {
        val expired = auth.filterValues { it.expiresAtMs != null && it.expiresAtMs <= nowMs }.keys.toList()
        expired.forEach { auth.remove(it) }
        return expired.size
    }

    private fun playbackQualityRank(quality: String): Int {
        return when (quality) {
            "lossless" -> 0
            "hires" -> 1
            "high" -> 2
            else -> 3
        }
    }
}

private fun searchResult(trackId: String): StreamingSearchResult {
    return StreamingSearchResult(
        provider = StreamingProviderName.NETEASE,
        query = "echo",
        page = 1,
        pageSize = 20,
        tracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = trackId,
                title = trackId,
                artist = "artist"
            )
        )
    )
}

private fun playlistDetail(trackId: String): StreamingPlaylistDetail {
    return StreamingPlaylistDetail(
        provider = StreamingProviderName.NETEASE,
        providerPlaylistId = "playlist-1",
        playlist = StreamingPlaylist(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-1",
            title = "Playlist",
            trackCount = 1
        ),
        tracks = listOf(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = trackId,
                title = trackId,
                artist = "artist"
            )
        ),
        total = 1
    )
}

private fun playbackSource(url: String, expiresAtEpochMs: Long? = null): StreamingPlaybackSource {
    return StreamingPlaybackSource(
        provider = StreamingProviderName.NETEASE,
        providerTrackId = "track-1",
        url = url,
        expiresAtEpochMs = expiresAtEpochMs
    )
}
