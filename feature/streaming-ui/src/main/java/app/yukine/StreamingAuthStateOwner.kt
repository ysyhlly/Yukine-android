package app.yukine

import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingCookieHeaderParser
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns provider discovery, authentication and foreground session maintenance. */
class StreamingAuthStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val stateOwner: StreamingFeatureStateOwner,
    private val repository: () -> StreamingRepository
) {
    private val sessionMaintenance by lazy {
        StreamingAuthSessionMaintenance(
            scope = scope,
            refresh = { provider -> repository().refreshAuthSession(provider) },
            diagnostics = { repository().diagnostics() },
            onAuthState = ::updateAuthState,
            onDiagnostics = ::updateDiagnostics
        )
    }

    fun refreshProviders(): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                val currentRepository = repository()
                val providers = currentRepository.providers()
                val capabilities = runCatching { currentRepository.providerCapabilities() }.getOrElse { emptyList() }
                val health = runCatching { currentRepository.providersHealth() }.getOrElse { emptyList() }
                val playbackPolicy = currentRepository.playbackSourcePolicy()
                val authStates = providers.associate { provider ->
                    provider.name to runCatching {
                        currentRepository.authState(provider.name)
                    }.getOrElse {
                        provider.auth
                    }
                }
                ProviderRefresh(providers, capabilities, health, authStates, playbackPolicy)
            }.onSuccess { refresh ->
                updateProviders(refresh.providers, refresh.capabilities, refresh.health)
                stateOwner.value = stateOwner.value.copy(
                    authStates = refresh.authStates,
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                    , playbackSourcePolicy = refresh.playbackPolicy
                )
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun updateProviders(
        providers: List<StreamingProviderDescriptor>,
        capabilities: List<StreamingProviderCapability> = stateOwner.value.providerCapabilities,
        health: List<StreamingProviderHealth> = stateOwner.value.providerHealth
    ) {
        val current = stateOwner.value
        val preferredProvider = providers.firstOrNull { provider ->
            provider.name != StreamingProviderName.MOCK &&
                ((current.authStates[provider.name] ?: provider.auth).connected)
        } ?: providers.firstOrNull { provider ->
            provider.name != StreamingProviderName.MOCK && provider.enabled
        } ?: providers.firstOrNull { provider -> provider.name != StreamingProviderName.MOCK }
        val selected = providers.firstOrNull {
            it.name == current.selectedProvider && current.selectedProvider != StreamingProviderName.MOCK
        }?.name ?: preferredProvider?.name ?: providers.firstOrNull()?.name ?: current.selectedProvider
        stateOwner.value = current.copy(
            providers = providers.toList(),
            providerCapabilities = capabilities.toList(),
            providerHealth = health.toList(),
            selectedProvider = selected
        )
    }

    fun selectProvider(provider: StreamingProviderName) {
        val current = stateOwner.value
        if (current.selectedProvider == provider) return
        stateOwner.value = current.copy(
            selectedProvider = provider,
            searchResult = null,
            resolvedPlaybackSource = null,
            resolvedPlaybackTrack = null,
            pendingAuthLaunch = null,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun refreshAuthState(provider: StreamingProviderName) {
        beginRequest()
        scope.launch {
            runCatching {
                repository().refreshAuthSession(provider, force = true)
            }.onSuccess { authState ->
                updateAuthState(provider, authState)
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun startAuth(
        provider: StreamingProviderName,
        redirectUri: String? = null,
        onLaunchReady: (() -> Unit)? = null
    ) {
        beginRequest()
        scope.launch {
            runCatching {
                repository().startAuth(provider, redirectUri)
            }.onSuccess { result ->
                updateAuthLaunch(provider, result.state, result.launchUrl)
                updateDiagnostics(repository().diagnostics())
                if (!result.launchUrl.isNullOrBlank()) onLaunchReady?.invoke()
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun signOut(provider: StreamingProviderName): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                repository().signOut(provider)
            }.onSuccess { authState ->
                updateAuthState(provider, authState)
                refreshProviders().join()
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun completeAuth(
        provider: StreamingProviderName,
        callbackUri: String,
        cookieHeader: String? = null,
        onAuthSuccess: StreamingCallback<StreamingProviderName>? = null
    ) {
        beginRequest()
        scope.launch {
            runCatching {
                repository().completeAuth(provider, callbackUri, cookieHeader)
            }.onSuccess { result ->
                updateAuthState(provider, result.state)
                refreshProviders().join()
                updateDiagnostics(repository().diagnostics())
                if (result.state.connected) onAuthSuccess?.onResult(provider)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun maintainSessions(): Job = sessionMaintenance.maintain()

    fun prepareManualCookieDialogState(
        provider: StreamingProviderName?,
        languageMode: String
    ): StreamingManualCookieDialogState {
        val unavailable = provider == null || provider in setOf(
            StreamingProviderName.MOCK,
            StreamingProviderName.M3U8,
            StreamingProviderName.PLUGIN
        )
        return StreamingManualCookieDialogState(
            provider = provider,
            unavailable = unavailable,
            title = text(languageMode, "streaming.manual.cookie"),
            hint = if (provider == StreamingProviderName.QQ_MUSIC) {
                text(languageMode, "streaming.cookie.hint.qq")
            } else if (provider == StreamingProviderName.KUGOU) {
                text(languageMode, "streaming.cookie.hint.kugou")
            } else {
                text(languageMode, "streaming.cookie.hint.default")
            },
            unavailableStatus = text(languageMode, "streaming.choose.login.provider")
        )
    }

    fun prepareManualCookieAuthRequest(
        provider: StreamingProviderName?,
        cookieHeader: String?,
        languageMode: String
    ): StreamingManualCookieAuthRequest? {
        val dialogState = prepareManualCookieDialogState(provider, languageMode)
        val cleanCookie = StreamingCookieHeaderParser.normalize(cookieHeader)
        val cleanProvider = dialogState.provider
        if (dialogState.unavailable || cleanProvider == null || cleanCookie.isEmpty()) return null
        return StreamingManualCookieAuthRequest(
            provider = cleanProvider,
            callbackUri = "$STREAMING_AUTH_REDIRECT_URI?provider=${cleanProvider.wireName}&manualCookie=1",
            cookieHeader = cleanCookie,
            emptyStatus = text(languageMode, "streaming.cookie.empty"),
            savedStatus = text(languageMode, "streaming.cookie.saved")
        )
    }

    fun manualCookieEmptyStatus(languageMode: String): String =
        text(languageMode, "streaming.cookie.empty")

    fun updateAuthState(provider: StreamingProviderName, authState: StreamingAuthState) {
        val current = stateOwner.value
        stateOwner.value = current.copy(
            authStates = current.authStates + (provider to authState),
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateAuthLaunch(
        provider: StreamingProviderName,
        authState: StreamingAuthState,
        launchUrl: String?
    ) {
        val cleanLaunchUrl = launchUrl?.takeIf { it.isNotBlank() }
        val current = stateOwner.value
        stateOwner.value = current.copy(
            authStates = current.authStates + (provider to authState),
            pendingAuthLaunch = cleanLaunchUrl?.let {
                StreamingSearchAuthLaunch(provider, it, authState.kind)
            },
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun clearAuthLaunch() {
        stateOwner.value = stateOwner.value.copy(pendingAuthLaunch = null)
    }

    private fun beginRequest() {
        stateOwner.value = stateOwner.value.copy(
            loading = true,
            loadingMore = false,
            errorMessage = null
        )
    }

    private fun failRequest(message: String?) {
        stateOwner.value = stateOwner.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message ?: "Streaming request failed"
        )
    }

    private fun updateDiagnostics(diagnostics: StreamingGatewayDiagnostics) {
        stateOwner.value = stateOwner.value.copy(diagnostics = diagnostics)
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)

    private data class ProviderRefresh(
        val providers: List<StreamingProviderDescriptor>,
        val capabilities: List<StreamingProviderCapability>,
        val health: List<StreamingProviderHealth>,
        val authStates: Map<StreamingProviderName, StreamingAuthState>
        , val playbackPolicy: app.yukine.streaming.PlaybackSourcePolicySnapshot
    )
}
