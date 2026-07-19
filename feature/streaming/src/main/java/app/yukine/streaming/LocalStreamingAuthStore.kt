package app.yukine.streaming

import android.content.Context
import android.content.SharedPreferences
import app.yukine.security.SecureSecretStore

/**
 * Persists streaming-provider login state captured locally on the device, usually from the
 * isolated WebView login flow. Remote gateway auth metadata is only a cache; this store is the
 * durable source that lets Yukine keep a streaming login across app restarts.
 */
interface StreamingLocalAuthStore {
    fun authState(provider: StreamingProviderName): StreamingAuthState

    fun saveLogin(
        provider: StreamingProviderName,
        cookieHeader: String?,
        displayName: String? = null
    ): StreamingAuthState

    fun signOut(provider: StreamingProviderName): StreamingAuthState

    fun cookieHeader(provider: StreamingProviderName): String?

    fun connected(provider: StreamingProviderName): Boolean

    /**
     * Whether encrypted credential material exists, including a credential already marked invalid.
     * This is deliberately distinct from [connected]: invalid credentials stay on device until the
     * user explicitly signs out or replaces them, so a temporary transport failure never erases a
     * login.
     */
    fun hasStoredCredential(provider: StreamingProviderName): Boolean = !cookieHeader(provider).isNullOrBlank()

    /** Returns true when a local credential is due for a lightweight platform verification. */
    fun needsSessionMaintenance(provider: StreamingProviderName, nowEpochMs: Long): Boolean = false

    fun markSessionMaintenanceStarted(provider: StreamingProviderName, nowEpochMs: Long) = Unit

    fun markVerified(provider: StreamingProviderName, verifiedAtEpochMs: Long): StreamingAuthState = authState(provider)

    fun markPendingVerification(
        provider: StreamingProviderName,
        message: String? = null
    ): StreamingAuthState = authState(provider)

    fun markInvalid(
        provider: StreamingProviderName,
        message: String? = null,
        checkedAtEpochMs: Long = System.currentTimeMillis()
    ): StreamingAuthState = authState(provider)

    /** Merges a platform-refreshed Cookie header without discarding fields absent from the update. */
    fun mergeRefreshedCookie(provider: StreamingProviderName, cookieHeader: String?): StreamingAuthState =
        saveLogin(provider, cookieHeader)
}

class LocalStreamingAuthStore(context: Context) : StreamingLocalAuthStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun authState(provider: StreamingProviderName): StreamingAuthState {
        val storedCookie = preferences.getString(keyCookie(provider), null)
        val cookie = SecureSecretStore.decryptOrPlain(storedCookie)
        val displayName = preferences.getString(keyDisplayName(provider), null)
        val hasCookie = !cookie.isNullOrBlank()
        val usableCookie = hasUsableCookie(provider, cookie)
        val decryptionFailed = SecureSecretStore.isVersionedCiphertext(storedCookie) && cookie == null
        val savedState = preferences.getString(keyCredentialState(provider), null)
            ?.let(StreamingCredentialState::fromWireName)
        val credentialState = when {
            decryptionFailed -> StreamingCredentialState.INVALID
            !hasCookie -> StreamingCredentialState.NOT_LOGGED_IN
            !usableCookie -> StreamingCredentialState.INVALID
            savedState == StreamingCredentialState.INVALID -> StreamingCredentialState.INVALID
            savedState != null -> savedState
            else -> StreamingCredentialState.PENDING_VERIFICATION
        }
        val isConnected = preferences.getBoolean(keyConnected(provider), false) &&
            usableCookie &&
            credentialState != StreamingCredentialState.INVALID &&
            (provider != StreamingProviderName.KUGOU ||
                credentialState == StreamingCredentialState.VALID)
        val lastVerifiedAt = preferences.getLong(keyLastVerified(provider), NO_TIMESTAMP)
            .takeIf { it != NO_TIMESTAMP }
        return StreamingAuthState(
            kind = providerAuthKind(provider),
            connected = isConnected,
            accountDisplayName = displayName?.takeIf { it.isNotBlank() },
            statusMessage = when {
                credentialState == StreamingCredentialState.VALID -> "本地登录有效"
                credentialState == StreamingCredentialState.PENDING_VERIFICATION ->
                    preferences.getString(keyStatusMessage(provider), null)
                        ?.takeIf { it.isNotBlank() }
                        ?: "本地登录待验证"
                decryptionFailed -> "登录凭据无法解密，请重新登录"
                hasCookie && !usableCookie && provider == StreamingProviderName.QQ_MUSIC ->
                    "QQ 音乐登录凭证不完整，请重新导入 Cookie"
                credentialState == StreamingCredentialState.INVALID ->
                    preferences.getString(keyStatusMessage(provider), null)
                        ?.takeIf { it.isNotBlank() }
                        ?: "登录凭据已失效，请重新登录"
                else -> "未登录"
            },
            credentialState = credentialState,
            lastVerifiedAtEpochMs = lastVerifiedAt
        )
    }

    override fun saveLogin(
        provider: StreamingProviderName,
        cookieHeader: String?,
        displayName: String?
    ): StreamingAuthState {
        val cookie = StreamingCookieHeaderParser.normalize(cookieHeader)
        if (cookie.isBlank()) {
            return authState(provider)
        }

        val usable = hasUsableCookie(provider, cookie)
        val editor = preferences.edit()
            .putBoolean(keyConnected(provider), usable)
            .putString(keyCookie(provider), SecureSecretStore.encryptOrPlain(cookie))
            .putString(
                keyCredentialState(provider),
                if (usable) {
                    StreamingCredentialState.PENDING_VERIFICATION.wireName
                } else {
                    StreamingCredentialState.INVALID.wireName
                }
            )
            .remove(keyLastVerified(provider))
            .remove(keyLastMaintenanceAttempt(provider))
            .remove(keyStatusMessage(provider))

        if (displayName.isNullOrBlank()) {
            editor.remove(keyDisplayName(provider))
        } else {
            editor.putString(keyDisplayName(provider), displayName.trim())
        }

        if (!editor.commit()) {
            return StreamingAuthState(
                kind = providerAuthKind(provider),
                connected = false,
                statusMessage = "登录状态保存失败，请重试"
            )
        }
        return authState(provider)
    }

    override fun signOut(provider: StreamingProviderName): StreamingAuthState {
        preferences.edit()
            .remove(keyConnected(provider))
            .remove(keyCookie(provider))
            .remove(keyDisplayName(provider))
            .remove(keyCredentialState(provider))
            .remove(keyLastVerified(provider))
            .remove(keyLastMaintenanceAttempt(provider))
            .remove(keyStatusMessage(provider))
            .commit()
        return authState(provider)
    }

    override fun cookieHeader(provider: StreamingProviderName): String? {
        return SecureSecretStore.decryptOrPlain(preferences.getString(keyCookie(provider), null))
            ?.takeIf { it.isNotBlank() }
    }

    override fun connected(provider: StreamingProviderName): Boolean = authState(provider).connected

    override fun hasStoredCredential(provider: StreamingProviderName): Boolean {
        return !preferences.getString(keyCookie(provider), null).isNullOrBlank()
    }

    override fun needsSessionMaintenance(provider: StreamingProviderName, nowEpochMs: Long): Boolean {
        val state = authState(provider)
        if (!state.connected || state.credentialState == StreamingCredentialState.INVALID) {
            return false
        }
        val lastAttempt = preferences.getLong(keyLastMaintenanceAttempt(provider), NO_TIMESTAMP)
        if (lastAttempt != NO_TIMESTAMP && nowEpochMs - lastAttempt < RETRY_INTERVAL_MS) {
            return false
        }
        val lastVerifiedAt = state.lastVerifiedAtEpochMs
        return state.credentialState == StreamingCredentialState.PENDING_VERIFICATION ||
            lastVerifiedAt == null ||
            nowEpochMs - lastVerifiedAt >= REVERIFY_INTERVAL_MS
    }

    override fun markSessionMaintenanceStarted(provider: StreamingProviderName, nowEpochMs: Long) {
        preferences.edit().putLong(keyLastMaintenanceAttempt(provider), nowEpochMs).apply()
    }

    override fun markVerified(provider: StreamingProviderName, verifiedAtEpochMs: Long): StreamingAuthState {
        val cookie = cookieHeader(provider)
        if (!hasUsableCookie(provider, cookie)) {
            return markInvalid(provider, checkedAtEpochMs = verifiedAtEpochMs)
        }
        preferences.edit()
            .putBoolean(keyConnected(provider), true)
            .putString(keyCredentialState(provider), StreamingCredentialState.VALID.wireName)
            .putLong(keyLastVerified(provider), verifiedAtEpochMs)
            .remove(keyStatusMessage(provider))
            .commit()
        return authState(provider)
    }

    override fun markPendingVerification(
        provider: StreamingProviderName,
        message: String?
    ): StreamingAuthState {
        val cookie = cookieHeader(provider)
        if (!hasUsableCookie(provider, cookie)) {
            return markInvalid(provider, message)
        }
        preferences.edit()
            .putBoolean(keyConnected(provider), true)
            .putString(keyCredentialState(provider), StreamingCredentialState.PENDING_VERIFICATION.wireName)
            .apply {
                if (message.isNullOrBlank()) remove(keyStatusMessage(provider))
                else putString(keyStatusMessage(provider), message.trim())
            }
            .commit()
        return authState(provider)
    }

    override fun markInvalid(
        provider: StreamingProviderName,
        message: String?,
        checkedAtEpochMs: Long
    ): StreamingAuthState {
        preferences.edit()
            .putBoolean(keyConnected(provider), false)
            .putString(keyCredentialState(provider), StreamingCredentialState.INVALID.wireName)
            .putLong(keyLastMaintenanceAttempt(provider), checkedAtEpochMs)
            .apply {
                if (message.isNullOrBlank()) remove(keyStatusMessage(provider))
                else putString(keyStatusMessage(provider), message.trim())
            }
            .commit()
        return authState(provider)
    }

    override fun mergeRefreshedCookie(
        provider: StreamingProviderName,
        cookieHeader: String?
    ): StreamingAuthState {
        val refreshed = StreamingCookieHeaderParser.normalize(cookieHeader)
        if (refreshed.isBlank()) {
            return authState(provider)
        }
        val merged = StreamingCookieHeaderParser.merge(cookieHeader(provider), refreshed)
        if (merged == cookieHeader(provider)) {
            return authState(provider)
        }
        return saveLogin(
            provider = provider,
            cookieHeader = merged,
            displayName = preferences.getString(keyDisplayName(provider), null)
        )
    }

    private fun hasUsableCookie(provider: StreamingProviderName, cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) {
            return false
        }
        val namesWithValue = StreamingCookieHeaderParser.namesWithValue(cookie)
        return LocalStreamingLoginEndpoints.hasSessionToken(provider, namesWithValue) &&
            (provider != StreamingProviderName.QQ_MUSIC || hasQqPlaybackCredential(cookie))
    }

    private fun keyConnected(provider: StreamingProviderName) = "connected:${provider.wireName}"
    private fun keyCookie(provider: StreamingProviderName) = "cookie:${provider.wireName}"
    private fun keyDisplayName(provider: StreamingProviderName) = "display:${provider.wireName}"
    private fun keyCredentialState(provider: StreamingProviderName) = "credential_state:${provider.wireName}"
    private fun keyLastVerified(provider: StreamingProviderName) = "last_verified:${provider.wireName}"
    private fun keyLastMaintenanceAttempt(provider: StreamingProviderName) = "last_maintenance_attempt:${provider.wireName}"
    private fun keyStatusMessage(provider: StreamingProviderName) = "credential_status:${provider.wireName}"

    companion object {
        const val PREFS_NAME: String = "streaming_local_auth"
        private const val NO_TIMESTAMP: Long = -1L
        private const val RETRY_INTERVAL_MS: Long = 15 * 60 * 1000L
        private const val REVERIFY_INTERVAL_MS: Long = 12 * 60 * 60 * 1000L

        fun providerAuthKind(provider: StreamingProviderName): StreamingAuthKind {
            return when (provider) {
                StreamingProviderName.NETEASE,
                StreamingProviderName.QQ_MUSIC,
                StreamingProviderName.KUGOU,
                StreamingProviderName.BILIBILI -> StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE
                StreamingProviderName.YOUTUBE,
                StreamingProviderName.SOUNDCLOUD,
                StreamingProviderName.SPOTIFY,
                StreamingProviderName.TIDAL -> StreamingAuthKind.CUSTOM_TABS_APP_LINK
                StreamingProviderName.MOCK,
                StreamingProviderName.M3U8,
                StreamingProviderName.LUOXUE,
                StreamingProviderName.PLUGIN -> StreamingAuthKind.NONE
            }
        }
    }
}
