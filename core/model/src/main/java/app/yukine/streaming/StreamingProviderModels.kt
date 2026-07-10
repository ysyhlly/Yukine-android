package app.yukine.streaming

data class StreamingProviderCapabilities(
    val supportsSearch: Boolean,
    val supportsPlayback: Boolean,
    val supportsLyrics: Boolean = false,
    val supportsMv: Boolean = false,
    val supportsAuth: Boolean = false,
    val supportsFavorites: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val supportedMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK)
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
    val actions: List<String> = emptyList()
)
