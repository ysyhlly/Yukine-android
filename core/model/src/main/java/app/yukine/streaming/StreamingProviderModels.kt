package app.yukine.streaming

data class StreamingProviderCapabilities(
    val supportsSearch: Boolean,
    val supportsPlayback: Boolean,
    val supportsLyrics: Boolean = false,
    val supportsMv: Boolean = false,
    val supportsAuth: Boolean = false,
    val supportsFavorites: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val supportedMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val supportsPlaylistImport: Boolean = supportsPlaylists,
    val supportsPlaylistReadSync: Boolean = supportsPlaylists,
    val supportsPlaylistCreate: Boolean = false,
    val supportsPlaylistWrite: Boolean = false,
    val supportsPlaylistDelete: Boolean = false,
    val supportsPlaylistRename: Boolean = false,
    val supportsPlaylistReorder: Boolean = false,
    val supportsFavoritesRead: Boolean = supportsFavorites,
    val supportsFavoritesWrite: Boolean = supportsFavorites,
    val supportsAudioResolve: Boolean = supportsPlayback,
    val supportsAudioFallback: Boolean = supportsPlayback,
    val supportsAudioDownload: Boolean = supportsPlayback,
    val supportsAudioCache: Boolean = supportsPlayback
)

data class StreamingAuthState(
    val kind: StreamingAuthKind = StreamingAuthKind.NONE,
    val connected: Boolean = false,
    val accountDisplayName: String? = null,
    val accountUsername: String? = null,
    val accountAvatarUrl: String? = null,
    val statusMessage: String? = null,
    val credentialState: StreamingCredentialState = if (connected) {
        StreamingCredentialState.VALID
    } else {
        StreamingCredentialState.NOT_LOGGED_IN
    },
    val lastVerifiedAtEpochMs: Long? = null
)

data class StreamingProviderDescriptor(
    val name: StreamingProviderName,
    val displayName: String,
    val enabled: Boolean = true,
    val capabilities: StreamingProviderCapabilities,
    val auth: StreamingAuthState = StreamingAuthState(),
    val status: StreamingProviderStatus = StreamingProviderStatus.READY,
    val statusMessage: String? = null
)

data class StreamingProviderHealth(
    val provider: StreamingProviderName,
    val available: Boolean,
    val authenticated: Boolean = false,
    val latencyMs: Long? = null,
    val errorCode: StreamingErrorCode? = null,
    val errorMessage: String? = null,
    val checkedAtEpochMs: Long? = null
)

data class StreamingProviderCapability(
    val provider: StreamingProviderName,
    val displayName: String,
    val enabled: Boolean,
    val status: StreamingProviderStatus = StreamingProviderStatus.READY,
    val supportsSearch: Boolean,
    val supportsPlayback: Boolean,
    val supportsAuth: Boolean = false,
    val supportsFavorites: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val supportsLyrics: Boolean = false,
    val supportsMv: Boolean = false,
    val supportedSearchMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val actions: List<String> = emptyList(),
    val supportsPlaylistImport: Boolean = supportsPlaylists,
    val supportsPlaylistReadSync: Boolean = supportsPlaylists,
    val supportsPlaylistCreate: Boolean = false,
    val supportsPlaylistWrite: Boolean = false,
    val supportsPlaylistDelete: Boolean = false,
    val supportsPlaylistRename: Boolean = false,
    val supportsPlaylistReorder: Boolean = false,
    val supportsFavoritesRead: Boolean = supportsFavorites,
    val supportsFavoritesWrite: Boolean = supportsFavorites,
    val supportsAudioResolve: Boolean = supportsPlayback,
    val supportsAudioFallback: Boolean = supportsPlayback,
    val supportsAudioDownload: Boolean = supportsPlayback,
    val supportsAudioCache: Boolean = supportsPlayback
)

/**
 * Audio policy is deliberately separate from account/library capabilities. QQ Music is a full
 * metadata and synchronization provider, but it is never an audio source in ECHO. Keeping this
 * hard rule here prevents a stale descriptor or cached URL from accidentally re-enabling it.
 */
object StreamingAudioCapabilityPolicy {
    @JvmStatic
    fun isPermanentlyMetadataOnly(provider: StreamingProviderName): Boolean =
        provider == StreamingProviderName.QQ_MUSIC

    @JvmStatic
    fun canResolve(descriptor: StreamingProviderDescriptor?): Boolean =
        descriptor != null &&
            !isPermanentlyMetadataOnly(descriptor.name) &&
            descriptor.enabled &&
            descriptor.capabilities.supportsPlayback &&
            descriptor.capabilities.supportsAudioResolve

    @JvmStatic
    fun canResolve(provider: StreamingProviderName, capability: StreamingProviderCapability?): Boolean =
        !isPermanentlyMetadataOnly(provider) &&
            capability?.enabled == true &&
            capability.supportsPlayback &&
            capability.supportsAudioResolve

    @JvmStatic
    fun canUsePlaybackUrl(provider: StreamingProviderName): Boolean =
        !isPermanentlyMetadataOnly(provider)

    @JvmStatic
    fun canFallback(provider: StreamingProviderName, capability: StreamingProviderCapability?): Boolean =
        canResolve(provider, capability) && capability?.supportsAudioFallback == true
}
