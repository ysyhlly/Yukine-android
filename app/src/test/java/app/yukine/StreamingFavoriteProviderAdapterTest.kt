package app.yukine

import app.yukine.streaming.StreamingAuthRequest
import app.yukine.streaming.StreamingAuthResult
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGateway
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistDetail
import app.yukine.streaming.StreamingPlaylistRequest
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingFavoriteProviderAdapterTest {
    @Test
    fun inboundCapabilityIsIndependentFromRemoteWritePolicy() = runTest {
        val gateway = FavoriteGateway(
            descriptors = listOf(
                descriptor(NETEASE, favoriteRead = true, favoriteWrite = true, auth = true),
                descriptor(BILIBILI, favoriteRead = false, favoriteWrite = true, playlists = true, auth = true),
                descriptor(PLUGIN, favoriteRead = false, favoriteWrite = false, playlists = true),
                descriptor(YOUTUBE)
            ),
            capabilities = listOf(
                capability(NETEASE, favoriteRead = true, favoriteWrite = true, auth = true),
                capability(BILIBILI, favoriteRead = false, favoriteWrite = true, playlists = true, auth = true),
                capability(PLUGIN, favoriteRead = false, favoriteWrite = false, playlists = true),
                capability(YOUTUBE)
            ),
            authStates = mapOf(
                NETEASE to StreamingAuthState(connected = true, accountId = "netease-user"),
                BILIBILI to StreamingAuthState(connected = true, accountId = "223344")
            )
        )
        val adapter = adapter(gateway)

        val result = adapter.capabilities().associateBy { it.provider }

        assertTrue(result.getValue(NETEASE).canPullFavorites)
        assertEquals("netease-user", result.getValue(NETEASE).accountId)
        assertTrue(result.getValue(NETEASE).canAddFavorite)
        assertFalse(result.getValue(BILIBILI).canPullFavorites)
        assertTrue(result.getValue(BILIBILI).canListCollections)
        assertFalse(result.getValue(BILIBILI).canAddFavorite)
        assertTrue(result.getValue(PLUGIN).canListCollections)
        assertFalse(result.getValue(YOUTUBE).canPullFavorites)
        assertFalse(result.getValue(YOUTUBE).canListCollections)
    }

    @Test
    fun bilibiliExposesOnlyExplicitFavoriteFolderSources() = runTest {
        val gateway = FavoriteGateway(
            descriptors = listOf(descriptor(BILIBILI, playlists = true, auth = true)),
            capabilities = listOf(capability(BILIBILI, playlists = true, auth = true)),
            authStates = mapOf(BILIBILI to StreamingAuthState(connected = true, accountId = "223344")),
            playlists = mapOf(
                BILIBILI to listOf(
                    StreamingPlaylist(BILIBILI, "favorite:11", "通勤"),
                    StreamingPlaylist(BILIBILI, "favorite:22", "现场")
                )
            )
        )
        val adapter = adapter(gateway)
        val capability = adapter.capabilities().single()

        val sources = adapter.sources(capability)

        assertEquals(listOf("通勤", "现场"), sources.map { it.displayName })
        assertTrue(sources.none { it.implicitLiked })
        assertEquals(
            setOf("bilibili:223344:favorite:11", "bilibili:223344:favorite:22"),
            sources.map { it.key }.toSet()
        )
        assertTrue(sources.all { it.deletionSafe })
    }

    @Test
    fun selectedPlaylistPullsEveryPageBeforeMarkingSnapshotComplete() = runTest {
        val firstPage = (1..40).map { track(BILIBILI, "video:BV$it") }
        val secondPage = listOf(track(BILIBILI, "video:BV41"))
        val gateway = FavoriteGateway(
            playlistPages = mapOf(
                1 to StreamingPlaylistDetail(
                    provider = BILIBILI,
                    providerPlaylistId = "favorite:11",
                    tracks = firstPage,
                    page = 1,
                    pageSize = 40,
                    hasMore = true
                ),
                2 to StreamingPlaylistDetail(
                    provider = BILIBILI,
                    providerPlaylistId = "favorite:11",
                    tracks = secondPage,
                    page = 2,
                    pageSize = 40,
                    hasMore = false
                )
            )
        )
        val adapter = adapter(gateway)
        val source = FavoriteSyncSource(
            key = "bilibili:223344:favorite:11",
            provider = BILIBILI,
            accountId = "223344",
            collectionId = "favorite:11",
            displayName = "通勤",
            implicitLiked = false
        )

        val delta = adapter.pullFavoriteDelta(source, null)

        assertEquals(41, delta.added.size)
        assertEquals(41, delta.observedProviderTrackIds.size)
        assertTrue(delta.completeSnapshot)
        assertEquals(listOf(1, 2), gateway.playlistRequests.map { it.page })
    }

    @Test
    fun emptyLikedCollectionIsACompleteSnapshotNotAnError() = runTest {
        val gateway = FavoriteGateway(liked = mapOf(QQ to emptyList()))
        val adapter = adapter(gateway)

        val delta = adapter.pullFavoriteDelta(
            QQ,
            FavoriteSyncCursor(
                provider = QQ,
                seenProviderTrackIds = setOf("qq-old"),
                baselineEstablished = true
            )
        )

        assertTrue(delta.added.isEmpty())
        assertTrue(delta.observedProviderTrackIds.isEmpty())
        assertEquals(setOf("qq-old"), delta.removedProviderTrackIds)
        assertTrue(delta.completeSnapshot)
    }

    private fun adapter(gateway: FavoriteGateway): StreamingFavoriteProviderAdapter =
        StreamingFavoriteProviderAdapter(
            object : StreamingRepositorySource {
                private val repository = StreamingRepository(gateway)
                override fun current(): StreamingRepository = repository
            }
        )

    private class FavoriteGateway(
        private val descriptors: List<StreamingProviderDescriptor> = emptyList(),
        private val capabilities: List<StreamingProviderCapability> = emptyList(),
        private val authStates: Map<StreamingProviderName, StreamingAuthState> = emptyMap(),
        private val playlists: Map<StreamingProviderName, List<StreamingPlaylist>> = emptyMap(),
        private val liked: Map<StreamingProviderName, List<StreamingTrack>> = emptyMap(),
        private val playlistPages: Map<Int, StreamingPlaylistDetail> = emptyMap()
    ) : StreamingGateway {
        val playlistRequests = mutableListOf<StreamingPlaylistRequest>()

        override suspend fun providers(): List<StreamingProviderDescriptor> = descriptors

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = capabilities

        override suspend fun providersHealth(): List<StreamingProviderHealth> = emptyList()

        override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
            StreamingSearchResult(
                provider = request.provider,
                query = request.query,
                page = request.page,
                pageSize = request.pageSize
            )

        override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
            playlistRequests += request
            return playlistPages[request.page] ?: StreamingPlaylistDetail(
                provider = request.provider,
                providerPlaylistId = request.providerPlaylistId,
                page = request.page,
                pageSize = request.pageSize
            )
        }

        override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> =
            playlists[provider].orEmpty()

        override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> =
            liked[provider].orEmpty()

        override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> =
            emptyList()

        override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> =
            emptyList()

        override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
            error("unused")

        override suspend fun authState(provider: StreamingProviderName): StreamingAuthState =
            authStates[provider] ?: StreamingAuthState()

        override suspend fun refreshAuthSession(
            provider: StreamingProviderName,
            force: Boolean
        ): StreamingAuthState = authState(provider)

        override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult =
            StreamingAuthResult(request.provider, authState(request.provider))

        override suspend fun completeAuth(
            provider: StreamingProviderName,
            callbackUri: String,
            cookieHeader: String?
        ): StreamingAuthResult = StreamingAuthResult(provider, authState(provider))

        override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState()
    }

    private fun descriptor(
        provider: StreamingProviderName,
        favoriteRead: Boolean = false,
        favoriteWrite: Boolean = false,
        playlists: Boolean = false,
        auth: Boolean = false
    ) = StreamingProviderDescriptor(
        name = provider,
        displayName = provider.wireName,
        capabilities = StreamingProviderCapabilities(
            supportsSearch = false,
            supportsPlayback = false,
            supportsAuth = auth,
            supportsPlaylists = playlists,
            supportsPlaylistReadSync = playlists,
            supportsFavoritesRead = favoriteRead,
            supportsFavoritesWrite = favoriteWrite
        )
    )

    private fun capability(
        provider: StreamingProviderName,
        favoriteRead: Boolean = false,
        favoriteWrite: Boolean = false,
        playlists: Boolean = false,
        auth: Boolean = false
    ) = StreamingProviderCapability(
        provider = provider,
        displayName = provider.wireName,
        enabled = true,
        supportsSearch = false,
        supportsPlayback = false,
        supportsAuth = auth,
        supportsPlaylists = playlists,
        supportsPlaylistReadSync = playlists,
        supportsFavoritesRead = favoriteRead,
        supportsFavoritesWrite = favoriteWrite
    )

    private fun track(provider: StreamingProviderName, id: String) = StreamingTrack(
        provider = provider,
        providerTrackId = id,
        title = id,
        artist = "artist"
    )

    private companion object {
        val NETEASE = StreamingProviderName.NETEASE
        val QQ = StreamingProviderName.QQ_MUSIC
        val BILIBILI = StreamingProviderName.BILIBILI
        val PLUGIN = StreamingProviderName.PLUGIN
        val YOUTUBE = StreamingProviderName.YOUTUBE
    }
}
