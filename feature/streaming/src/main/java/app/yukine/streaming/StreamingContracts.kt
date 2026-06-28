package app.yukine.streaming

interface StreamingProvider {
    val descriptor: StreamingProviderDescriptor

    suspend fun search(request: StreamingSearchRequest): StreamingSearchResult

    suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource

    suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        return StreamingPlaylistDetail(
            provider = request.provider,
            providerPlaylistId = request.providerPlaylistId
        )
    }

    suspend fun authState(): StreamingAuthState = descriptor.auth

    suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
        return StreamingAuthResult(request.provider, authState(), statusMessage = "Auth is not supported")
    }

    suspend fun completeAuth(callbackUri: String): StreamingAuthResult {
        return StreamingAuthResult(descriptor.name, authState(), statusMessage = "Auth is not supported")
    }

    suspend fun signOut(): StreamingAuthState = authState()

    suspend fun health(): StreamingProviderHealth {
        val state = authState()
        return StreamingProviderHealth(
            provider = descriptor.name,
            available = descriptor.enabled && descriptor.status != StreamingProviderStatus.ERROR,
            authenticated = state.connected,
            errorCode = if (descriptor.status == StreamingProviderStatus.ERROR) StreamingErrorCode.SOURCE_UNAVAILABLE else null,
            errorMessage = descriptor.statusMessage ?: state.statusMessage
        )
    }
}
