package app.yukine.streaming

import app.yukine.streaming.cache.StreamingAuthMetadataEntity
import app.yukine.streaming.cache.StreamingCacheDao
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import app.yukine.streaming.cache.StreamingPlaybackCacheEntity
import app.yukine.streaming.cache.StreamingPlaylistCacheEntity
import app.yukine.streaming.cache.StreamingSearchCacheEntity
import app.yukine.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamingRepositoryTest {
    @Test
    fun persistentHeadersReplaceExpiredPersistedUrlWithFreshCachedUrl() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 3_000L }
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "track-restore",
            StreamingAudioQuality.HIGH,
            StreamingGatewayJson.playbackSourceJson(
                playbackSource("https://fresh.example.test/track.mp3").copy(
                    providerTrackId = "track-restore"
                )
            ),
            ttlMs = 60_000L
        )
        val persisted = StreamingPlaybackAdapter.toTrack(
            playbackSource("https://expired.example.test/track.mp3").copy(
                providerTrackId = "track-restore"
            )
        )
        assertNotNull(cache.cachedPlaybackBlocking(StreamingProviderName.NETEASE, "track-restore"))

        val restored = PersistentStreamingPlaybackHeaders(cache).restoredTrackFor(persisted)

        assertNotNull(restored)
        assertEquals(persisted.dataPath, restored?.dataPath)
    }

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
    fun searchSkipsCacheWriteWhenCacheIsDisabled() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 2_000L }
        val repository = StreamingRepository(
            gateway = FakeStreamingGateway(searchResult = searchResult("remote-track")),
            cache = cache
        )

        repository.search(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            useCache = false
        )

        assertTrue(dao.searches.isEmpty())
    }

    @Test
    fun searchDoesNotCacheEmptyGatewayResult() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 2_000L }
        val repository = StreamingRepository(
            gateway = FakeStreamingGateway(),
            cache = cache
        )

        repository.search(
            provider = StreamingProviderName.NETEASE,
            query = "missing"
        )

        assertTrue(dao.searches.isEmpty())
    }

    @Test
    fun resolvePlaybackUsesAndWritesCache() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 3_000L }
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/remote-url.mp3")
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
        val forced = repository.resolvePlayback(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.LOSSLESS,
            forceRefresh = true
        )

        assertEquals("https://stream.example.test/remote-url.mp3", first.url)
        assertEquals("https://stream.example.test/remote-url.mp3", second.url)
        assertEquals("https://stream.example.test/remote-url.mp3", forced.url)
        assertEquals(2, gateway.playbackRequests.size)
        assertEquals(
            3_088L,
            dao.playbacks[Triple("netease", "track-1", "lossless")]?.expiresAtMs
        )
    }

    @Test
    fun luoxuePlaybackCacheSeparatesDifferentMusicInfoPayloads() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 3_000L }
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/lx-url.mp3")
        )
        val repository = StreamingRepository(gateway = gateway, cache = cache)
        val firstInfo = """{"hash":"same","album_id":"one"}"""
        val secondInfo = """{"hash":"same","album_id":"two"}"""

        repository.resolvePlayback(
            StreamingProviderName.LUOXUE,
            "kg:same.0.0",
            StreamingAudioQuality.LOSSLESS,
            firstInfo
        )
        repository.resolvePlayback(
            StreamingProviderName.LUOXUE,
            "kg:same.0.0",
            StreamingAudioQuality.LOSSLESS,
            firstInfo
        )
        repository.resolvePlayback(
            StreamingProviderName.LUOXUE,
            "kg:same.0.0",
            StreamingAudioQuality.LOSSLESS,
            secondInfo
        )

        assertEquals(2, gateway.playbackRequests.size)
        assertEquals(firstInfo, gateway.playbackRequests[0].luoxueMusicInfoJson)
        assertEquals(secondInfo, gateway.playbackRequests[1].luoxueMusicInfoJson)
        assertEquals(2, dao.playbacks.size)
    }

    @Test
    fun qqCachedPlaybackCannotBypassFixedDisabledPolicy() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 3_000L }
        cache.savePlayback(
            StreamingProviderName.QQ_MUSIC,
            "track-1",
            StreamingAudioQuality.STANDARD,
            StreamingGatewayJson.playbackSourceJson(
                playbackSource("163.125.230.232;invalid;")
                    .copy(provider = StreamingProviderName.QQ_MUSIC, providerTrackId = "track-1")
            ),
            ttlMs = 60_000L
        )
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/refreshed.mp3")
        )
        val repository = StreamingRepository(gateway = gateway, cache = cache)

        val result = runCatching {
            repository.resolvePlayback(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "track-1",
                quality = StreamingAudioQuality.STANDARD
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, gateway.playbackRequests.size)
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
            playbackSource = playbackSource("https://stream.example.test/expiring.mp3", expiresAtEpochMs = 15_000L)
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
    fun resolvePlaybackTrackRanksLuoxueSearchResultsAfterConfirmedNeteaseIdFails() = runTest {
        val telemetryEvents = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val firstResult = StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "priority-first",
            title = "Completely different title",
            artist = "Different artist"
        )
        val preciseResult = StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "precise-second",
            title = "Echo",
            artist = "Tester"
        )
        val gateway = FakeStreamingGateway(
            searchResult = StreamingSearchResult(
                provider = StreamingProviderName.LUOXUE,
                query = "Echo",
                page = 1,
                pageSize = 8,
                tracks = listOf(firstResult, preciseResult)
            ),
            playbackSource = playbackSource("https://stream.example.test/track-1.m3u8"),
            playbackFailures = ArrayDeque(
                listOf(
                    StreamingGatewayException(
                        "stale provider id",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                )
            )
        )
        val adapter = FakePlaybackTrackAdapter()
        val repository = StreamingRepository(
            gateway = gateway,
            playbackTrackAdapter = adapter,
            playbackSourcePolicy = playbackPolicy(StreamingProviderName.NETEASE),
            playbackTelemetry = StreamingPlaybackTelemetry(telemetryEvents::add)
        )
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "stale-track",
            title = "Echo",
            artist = "Tester"
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "stale-track",
            quality = StreamingAudioQuality.HIGH,
            metadata = metadata
        )

        assertEquals("https://stream.example.test/track-1.m3u8", result.source.url)
        assertEquals(StreamingPlaybackResolutionPath.TITLE_SEARCH, result.resolutionPath)
        assertEquals("adapter-precise-second", result.track.title)
        assertEquals(listOf("precise-second"), adapter.sourceTrackIds)
        assertEquals(
            listOf(metadata.copy(provider = StreamingProviderName.LUOXUE, providerTrackId = "precise-second")),
            adapter.metadataRequests
        )
        assertEquals("Echo", gateway.searchRequests.single().query)
        assertEquals(StreamingProviderName.LUOXUE, gateway.searchRequests.single().provider)
        assertEquals(setOf(StreamingMediaType.TRACK), gateway.searchRequests.single().mediaTypes)
        assertEquals(5, gateway.searchRequests.single().pageSize)
        assertEquals(
            listOf("stale-track", "precise-second"),
            gateway.playbackRequests.map { it.providerTrackId }
        )
        assertEquals(0, gateway.capabilityCalls)
        assertEquals(
            listOf(
                StreamingPlaybackTelemetryStage.URL_RESOLVE,
                StreamingPlaybackTelemetryStage.TITLE_SEARCH,
                StreamingPlaybackTelemetryStage.CANDIDATE_RANK,
                StreamingPlaybackTelemetryStage.URL_RESOLVE
            ),
            telemetryEvents.map { it.stage }
        )
        assertFalse(telemetryEvents[0].success)
        assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, telemetryEvents[0].errorCode)
        assertEquals(2, telemetryEvents[1].candidateCount)
        assertEquals("precise-second", telemetryEvents[2].providerTrackId)
        assertTrue(telemetryEvents[3].success)
        assertEquals(StreamingPlaybackResolutionPath.TITLE_SEARCH, telemetryEvents[3].resolutionPath)
    }

    @Test
    fun resolvePlaybackTrackAllowsLuoxueTxCandidateWithoutDirectQqRequest() = runTest {
        val gateway = FakeStreamingGateway(
            searchResult = StreamingSearchResult(
                provider = StreamingProviderName.LUOXUE,
                query = "Echo",
                page = 1,
                pageSize = 5,
                tracks = listOf(
                    StreamingTrack(
                        provider = StreamingProviderName.LUOXUE,
                        providerTrackId = "tx:qq-song-mid",
                        title = "Echo",
                        artist = "Artist"
                    ),
                    StreamingTrack(
                        provider = StreamingProviderName.LUOXUE,
                        providerTrackId = "kg:kugou-hash.1.0",
                        title = "Different song",
                        artist = "Different artist"
                    )
                )
            ),
            playbackSource = playbackSource("https://stream.example.test/echo.flac")
        )
        val repository = StreamingRepository(gateway = gateway)

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = "qq-identity-only",
            metadata = StreamingTrack(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "qq-identity-only",
                title = "Echo",
                artist = "Artist"
            )
        )

        assertEquals("tx:qq-song-mid", result.source.providerTrackId)
        assertEquals(1, gateway.playbackRequests.size)
        assertEquals(StreamingProviderName.LUOXUE, gateway.playbackRequests.single().provider)
        assertEquals("tx:qq-song-mid", gateway.playbackRequests.single().providerTrackId)
    }

    @Test
    fun resolvePlaybackTrackAllowsSlowLuoxueTxWithinDedicatedBudget() = runTest {
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                delay(3_000L)
                return playbackSource("https://stream.example.test/slow-tx.flac").copy(
                    provider = request.provider,
                    providerTrackId = request.providerTrackId
                )
            }
        }
        val repository = StreamingRepository(gateway = gateway)

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "tx:private-song-mid"
        )

        assertEquals(StreamingProviderName.LUOXUE, result.source.provider)
        assertEquals("tx:private-song-mid", result.source.providerTrackId)
    }

    @Test
    fun resolvePlaybackTrackSynthesizesCanonicalMetadataForLuoxueTx() = runTest {
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/tx-with-metadata.flac")
        )
        val repository = StreamingRepository(gateway = gateway)

        repository.resolvePlaybackTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "tx:001qrnDh07q133|media-mid",
            metadata = StreamingTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "tx:001qrnDh07q133|media-mid",
                title = "测试歌曲",
                artist = "测试歌手",
                album = "测试专辑",
                durationMs = 243_000L
            )
        )

        val payload = JSONObject(gateway.playbackRequests.single().luoxueMusicInfoJson.orEmpty())
        assertEquals("测试歌曲", payload.getString("name"))
        assertEquals("测试歌手", payload.getString("singer"))
        assertEquals("001qrnDh07q133", payload.getString("songmid"))
        assertEquals("media-mid", payload.getString("mediaMid"))
        assertEquals(243L, payload.getLong("interval"))
    }

    @Test
    fun ordinaryPlaybackTimeoutDoesNotExposeProviderTrackId() = runTest {
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                delay(3_000L)
                return playbackSource("https://stream.example.test/too-slow.flac")
            }
        }
        val repository = StreamingRepository(gateway = gateway)

        try {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "private-provider-track-id"
            )
            fail("Expected playback resolution to time out")
        } catch (error: StreamingGatewayException) {
            assertEquals("Playback source resolution timed out", error.message)
            assertFalse(error.message.orEmpty().contains("private-provider-track-id"))
        }
    }

    @Test
    fun resolvePlaybackTrackRanksOriginalAheadOfEarlierLiveLuoxueResult() = runTest {
        val gateway = FakeStreamingGateway(
            searchResult = StreamingSearchResult(
                provider = StreamingProviderName.LUOXUE,
                query = "Echo",
                page = 1,
                pageSize = 5,
                tracks = listOf(
                    StreamingTrack(
                        provider = StreamingProviderName.LUOXUE,
                        providerTrackId = "echo-live",
                        title = "Echo (Live)",
                        artist = "Tester",
                        durationMs = 240_000L
                    ),
                    StreamingTrack(
                        provider = StreamingProviderName.LUOXUE,
                        providerTrackId = "echo-original",
                        title = "Echo",
                        artist = "Tester",
                        durationMs = 180_000L
                    )
                )
            ),
            playbackSource = playbackSource("https://stream.example.test/echo.flac"),
            playbackFailures = ArrayDeque(
                listOf(
                    StreamingGatewayException(
                        "stale provider id",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                )
            )
        )
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(StreamingProviderName.NETEASE)
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "stale-track",
            metadata = StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "stale-track",
                title = "Echo",
                artist = "Tester",
                durationMs = 180_000L
            )
        )

        assertEquals("echo-original", result.source.providerTrackId)
        assertEquals(
            listOf("stale-track", "echo-original"),
            gateway.playbackRequests.map { it.providerTrackId }
        )
        assertEquals(1, gateway.searchRequests.size)
        val shadow = repository.diagnostics().recentLogs.firstOrNull {
            it.operation == "playback_match_shadow_v2"
        }
        assertNotNull(shadow)
        assertTrue(shadow?.message.orEmpty().contains("v1Top1=echo-original"))
        assertTrue(shadow?.message.orEmpty().contains("v2Top1=echo-original"))
        assertTrue(
            shadow?.message.orEmpty().contains(
                "scoreVersion=${RecordingMatchEvaluatorV2.SCORE_VERSION}"
            )
        )
    }

    @Test
    fun resolvePlaybackTrackUsesKnownIdWithoutBlockingOnTitleSearch() = runTest {
        val events = mutableListOf<String>()
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
                events += "search:${request.query}"
                return StreamingSearchResult(
                    provider = request.provider,
                    query = request.query,
                    page = request.page,
                    pageSize = request.pageSize
                )
            }

            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                events += "playback:${request.providerTrackId}"
                return playbackSource("https://stream.example.test/original.mp3").copy(
                    provider = request.provider,
                    providerTrackId = request.providerTrackId
                )
            }
        }
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(StreamingProviderName.NETEASE)
        )
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "original-id",
            title = "Echo",
            artist = "Tester"
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "original-id",
            quality = StreamingAudioQuality.HIGH,
            metadata = metadata
        )

        assertEquals("https://stream.example.test/original.mp3", result.source.url)
        assertEquals(StreamingPlaybackResolutionPath.KNOWN_PROVIDER_ID, result.resolutionPath)
        assertEquals(listOf("playback:original-id"), events)
    }

    @Test
    fun resolvePlaybackTrackReportsFreshUrlCacheWithoutGatewayOrSearch() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 1_000L }
        cache.savePlayback(
            StreamingProviderName.NETEASE,
            "cached-id",
            StreamingAudioQuality.HIGH,
            StreamingGatewayJson.playbackSourceJson(
                playbackSource("https://cache.example.test/cached.flac").copy(providerTrackId = "cached-id")
            ),
            ttlMs = 60_000L
        )
        val gateway = FakeStreamingGateway()
        val repository = StreamingRepository(
            gateway = gateway,
            cache = cache,
            playbackSourcePolicy = playbackPolicy(StreamingProviderName.NETEASE)
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "cached-id",
            quality = StreamingAudioQuality.HIGH,
            metadata = StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "cached-id",
                title = "Cached song",
                artist = "Artist"
            )
        )

        assertEquals(StreamingPlaybackResolutionPath.URL_CACHE, result.resolutionPath)
        assertEquals("https://cache.example.test/cached.flac", result.source.url)
        assertTrue(gateway.playbackRequests.isEmpty())
        assertTrue(gateway.searchRequests.isEmpty())
    }

    @Test
    fun resolvePlaybackTrackNeverTitleSearchesNeteaseAndUsesLuoxueFallback() = runTest {
        val telemetryEvents = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            val searchedProviders = mutableListOf<StreamingProviderName>()

            override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
                searchedProviders += request.provider
                return StreamingSearchResult(
                    provider = request.provider,
                    query = request.query,
                    page = request.page,
                    pageSize = request.pageSize,
                    tracks = listOf(
                        StreamingTrack(
                            provider = request.provider,
                            providerTrackId = "mock-first",
                            title = "Echo",
                            artist = "Artist"
                        )
                    )
                )
            }

            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                if (request.providerTrackId == "known-stale") {
                    throw StreamingGatewayException(
                        "stale",
                        code = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                }
                return playbackSource("https://mock.example.test/first.flac").copy(
                    provider = request.provider,
                    providerTrackId = request.providerTrackId
                )
            }
        }
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(
                StreamingProviderName.NETEASE,
                StreamingProviderName.LUOXUE
            ),
            titleSearchTimeoutMs = 100L,
            playbackTelemetry = StreamingPlaybackTelemetry(telemetryEvents::add)
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "known-stale",
            metadata = StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "known-stale",
                title = "Echo",
                artist = "Artist"
            )
        )

        assertEquals(StreamingProviderName.LUOXUE, result.source.provider)
        assertEquals(StreamingPlaybackResolutionPath.TITLE_SEARCH, result.resolutionPath)
        assertEquals(
            listOf(StreamingProviderName.LUOXUE),
            gateway.searchedProviders
        )
        assertTrue(telemetryEvents.none {
            it.stage == StreamingPlaybackTelemetryStage.TITLE_SEARCH &&
                it.provider == StreamingProviderName.NETEASE
        })
    }

    @Test
    fun resolvePlaybackTrackNeverSubstitutesAnotherProviderForBilibili() = runTest {
        val gateway = FakeStreamingGateway(playbackSource = playbackSource(""))
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(
                StreamingProviderName.BILIBILI,
                StreamingProviderName.NETEASE,
                StreamingProviderName.LUOXUE
            )
        )
        val metadata = StreamingTrack(
            provider = StreamingProviderName.BILIBILI,
            providerTrackId = "video:BV1TEST:cid:42",
            title = "Echo",
            artist = "UP 主",
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "netease-substitute"
                ),
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "kw:luoxue-substitute"
                )
            )
        )

        try {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.BILIBILI,
                providerTrackId = "video:BV1TEST:cid:42",
                metadata = metadata
            )
            fail("Expected the unavailable Bilibili source to fail without substitution")
        } catch (error: StreamingGatewayException) {
            assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, error.code)
        }

        assertEquals(1, gateway.playbackRequests.size)
        assertEquals(StreamingProviderName.BILIBILI, gateway.playbackRequests.single().provider)
        assertEquals("video:BV1TEST:cid:42", gateway.playbackRequests.single().providerTrackId)
        assertTrue(gateway.searchRequests.isEmpty())
    }

    @Test
    fun resolvePlaybackTrackRejectsGatewaySourceFromAnotherProviderForBilibili() = runTest {
        var searchCalls = 0
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
                searchCalls += 1
                return StreamingSearchResult(
                    provider = request.provider,
                    query = request.query,
                    page = request.page,
                    pageSize = request.pageSize
                )
            }

            override suspend fun resolvePlayback(
                request: StreamingPlaybackRequest
            ): StreamingPlaybackSource {
                return playbackSource("https://netease.example.test/substitute.flac").copy(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "netease-substitute"
                )
            }
        }
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(
                StreamingProviderName.BILIBILI,
                StreamingProviderName.LUOXUE
            )
        )

        try {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.BILIBILI,
                providerTrackId = "video:BV1TEST:cid:42",
                metadata = StreamingTrack(
                    provider = StreamingProviderName.BILIBILI,
                    providerTrackId = "video:BV1TEST:cid:42",
                    title = "Bilibili Video",
                    artist = "UP 主"
                )
            )
            fail("Expected a non-Bilibili gateway source to be rejected")
        } catch (error: StreamingGatewayException) {
            assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, error.code)
        }

        assertEquals(0, searchCalls)
    }

    @Test
    fun resolvePlaybackTrackDoesNotBlockOnCdnPreflight() = runTest {
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://10.255.255.1/slow-track.flac")
        )
        val repository = StreamingRepository(
            gateway = gateway,
            playbackTrackAdapter = object : StreamingPlaybackTrackAdapter {
                override fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack?): Track {
                    return Track(1L, "Echo", "Tester", "Album", 0L, android.net.Uri.EMPTY, "streaming:netease:track-1")
                }
            }
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            quality = StreamingAudioQuality.HIGH,
            metadata = null
        )

        assertEquals("https://10.255.255.1/slow-track.flac", result.source.url)
        assertTrue(repository.diagnostics().recentLogs.none { it.operation == "playback_preflight" })
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
    fun resolvePlaybackTrackUsesConfirmedNeteaseBeforeLuoxueCandidate() = runTest {
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            val requestedProviders = mutableListOf<StreamingProviderName>()
            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                requestedProviders += request.provider
                return playbackSource("https://stream.example.test/lx-echo.flac")
                    .copy(provider = request.provider, providerTrackId = request.providerTrackId)
            }
        }
        val repository = StreamingRepository(gateway = gateway)
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            title = "Echo",
            artist = "Artist",
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "lx-echo"
                )
            )
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            quality = StreamingAudioQuality.HIGH,
            metadata = metadata
        )

        assertEquals("https://stream.example.test/lx-echo.flac", result.source.url)
        assertEquals(StreamingProviderName.NETEASE, result.source.provider)
        assertEquals(listOf(StreamingProviderName.NETEASE), gateway.requestedProviders)
    }

    @Test
    fun resolvePlaybackTrackTriesLuoxueOnlyAfterConfirmedNeteaseFails() = runTest {
        val requestedProviders = mutableListOf<StreamingProviderName>()
        val telemetryEvents = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                requestedProviders += request.provider
                if (request.provider == StreamingProviderName.NETEASE) {
                    throw StreamingGatewayException("stale", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
                }
                return playbackSource("https://stream.example.test/${request.provider.wireName}.flac")
                    .copy(provider = request.provider, providerTrackId = request.providerTrackId)
            }
        }
        val repository = StreamingRepository(
            gateway = gateway,
            playbackSourcePolicy = playbackPolicy(
                StreamingProviderName.LUOXUE,
                StreamingProviderName.NETEASE
            ),
            playbackTelemetry = StreamingPlaybackTelemetry(telemetryEvents::add)
        )
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            title = "Echo",
            artist = "Artist",
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "lx-echo"
                )
            )
        )

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            metadata = metadata
        )

        assertEquals(StreamingProviderName.LUOXUE, result.source.provider)
        assertEquals(
            listOf(StreamingProviderName.NETEASE, StreamingProviderName.LUOXUE),
            requestedProviders
        )
        assertTrue(
            telemetryEvents.any {
                it.provider == StreamingProviderName.NETEASE && !it.success
            }
        )
        assertTrue(
            telemetryEvents.any {
                it.provider == StreamingProviderName.LUOXUE && it.success
            }
        )
    }

    @Test
    fun resolvePlaybackTrackPassesCandidateLuoxueMusicInfoToFallback() = runTest {
        val musicInfo = """{"hash":"lx-hash","album_id":"22"}"""
        val requests = mutableListOf<StreamingPlaybackRequest>()
        val gateway = object : StreamingGateway by FakeStreamingGateway() {
            override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
                requests += request
                return if (request.provider == StreamingProviderName.NETEASE) {
                    playbackSource("").copy(provider = request.provider, providerTrackId = request.providerTrackId)
                } else {
                    playbackSource("https://stream.example.test/lx-echo.flac")
                        .copy(provider = request.provider, providerTrackId = request.providerTrackId)
                }
            }
        }
        val repository = StreamingRepository(gateway = gateway)
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            title = "Echo",
            artist = "Artist",
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "kg:lx-hash.22.0",
                    luoxueMusicInfoJson = musicInfo
                )
            )
        )

        repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            quality = StreamingAudioQuality.HIGH,
            metadata = metadata
        )

        assertEquals(2, requests.size)
        assertEquals(StreamingProviderName.NETEASE, requests[0].provider)
        assertEquals(StreamingProviderName.LUOXUE, requests[1].provider)
        assertEquals(musicInfo, requests[1].luoxueMusicInfoJson)
    }

    @Test
    fun resolvePlaybackTrackFailsWhenAllCandidateSourcesFail() = runTest {
        val gateway = FakeStreamingGateway(playbackSource = playbackSource(""))
        val repository = StreamingRepository(gateway = gateway)
        val metadata = StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "netease-echo",
            title = "Echo",
            artist = "Artist",
            playbackCandidates = listOf(
                StreamingPlaybackCandidate(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "qq-echo"
                )
            )
        )

        try {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "netease-echo",
                quality = StreamingAudioQuality.HIGH,
                metadata = metadata
            )
            fail("Expected playback resolution to fail when all sources are unavailable")
        } catch (error: StreamingGatewayException) {
            assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, error.code)
        }
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
    fun changingLuoxueSourcesClearsSearchAndPlaybackButKeepsOtherProviderCache() = runTest {
        val dao = FakeStreamingCacheDao()
        val cache = StreamingCacheRepository(dao) { 25_000L }
        val lxRequest = StreamingSearchRequest(StreamingProviderName.LUOXUE, "echo")
        val qqRequest = StreamingSearchRequest(StreamingProviderName.QQ_MUSIC, "echo")
        cache.saveSearch(lxRequest, """{"provider":"lx"}""", ttlMs = 1_000L)
        cache.saveSearch(qqRequest, """{"provider":"qq"}""", ttlMs = 1_000L)
        cache.savePlayback(
            StreamingProviderName.LUOXUE,
            "tx:track",
            StreamingAudioQuality.HIGH,
            """{"url":"https://lx.example.test/song"}""",
            ttlMs = 1_000L
        )

        assertEquals(2, cache.clearSearchAndPlaybackForProvider(StreamingProviderName.LUOXUE))

        assertEquals(null, cache.cachedSearch(lxRequest))
        assertEquals(null, cache.cachedPlayback(StreamingProviderName.LUOXUE, "tx:track", StreamingAudioQuality.HIGH))
        assertEquals("""{"provider":"qq"}""", cache.cachedSearch(qqRequest))
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

    @Test
    fun resolvePlaybackTrackRefreshesAuthOnceThenRetriesTheSameSource() = runTest {
        val gateway = FakeStreamingGateway(
            playbackSource = playbackSource("https://stream.example.test/refreshed.mp3"),
            playbackFailures = ArrayDeque(
                listOf(
                    StreamingGatewayException(
                        "session expired",
                        code = StreamingErrorCode.AUTH_REQUIRED
                    )
                )
            ),
            refreshedAuthState = StreamingAuthState(
                kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                connected = true,
                credentialState = StreamingCredentialState.VALID
            )
        )
        val repository = StreamingRepository(gateway)

        val result = repository.resolvePlaybackTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1"
        )

        assertEquals("https://stream.example.test/refreshed.mp3", result.source.url)
        assertEquals(2, gateway.playbackRequests.size)
        assertEquals(listOf("track-1", "track-1"), gateway.playbackRequests.map { it.providerTrackId })
        assertEquals(1, gateway.refreshAuthCalls)
    }
}

private class FakeStreamingGateway(
    private val searchResult: StreamingSearchResult = StreamingSearchResult(
        provider = StreamingProviderName.NETEASE,
        query = "",
        page = 1,
        pageSize = 20
    ),
    private val playlistDetail: StreamingPlaylistDetail = playlistDetail("playlist-track"),
    private val playbackSource: StreamingPlaybackSource = playbackSource("url"),
    private val authState: StreamingAuthState = StreamingAuthState(),
    private val health: List<StreamingProviderHealth> = emptyList(),
    private val capabilities: List<StreamingProviderCapability> = emptyList(),
    private val playbackFailures: ArrayDeque<StreamingGatewayException> = ArrayDeque(),
    private val refreshedAuthState: StreamingAuthState? = null
) : StreamingGateway {
    val searchRequests = mutableListOf<StreamingSearchRequest>()
    val playlistRequests = mutableListOf<StreamingPlaylistRequest>()
    val playbackRequests = mutableListOf<StreamingPlaybackRequest>()
    val authStateProviders = mutableListOf<StreamingProviderName>()
    var refreshAuthCalls = 0
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
        if (playbackFailures.isNotEmpty()) {
            throw playbackFailures.removeFirst()
        }
        return playbackSource.copy(
            provider = request.provider,
            providerTrackId = request.providerTrackId
        )
    }

    override suspend fun authState(provider: StreamingProviderName): StreamingAuthState {
        authStateProviders += provider
        return authState
    }

    override suspend fun refreshAuthSession(
        provider: StreamingProviderName,
        force: Boolean
    ): StreamingAuthState {
        refreshAuthCalls += 1
        return refreshedAuthState ?: authState
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

    override suspend fun deleteSearchForProvider(provider: String): Int {
        val keys = searches.filterKeys { it.first == provider }.keys.toList()
        keys.forEach(searches::remove)
        return keys.size
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

    override suspend fun deletePlaybackForProvider(provider: String): Int {
        val keys = playbacks.filterKeys { it.first == provider }.keys.toList()
        keys.forEach(playbacks::remove)
        return keys.size
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

private fun playbackPolicy(vararg providers: StreamingProviderName): PlaybackSourcePolicy {
    val enabled = providers.toSet()
    return object : PlaybackSourcePolicy {
        override fun snapshot(): PlaybackSourcePolicySnapshot = PlaybackSourcePolicySnapshot(
            enabledRemoteProviders = enabled,
            remotePriority = providers.toList()
        )
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
