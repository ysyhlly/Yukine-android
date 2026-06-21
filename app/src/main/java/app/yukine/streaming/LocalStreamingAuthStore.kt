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
}

class LocalStreamingAuthStore(context: Context) : StreamingLocalAuthStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun authState(provider: StreamingProviderName): StreamingAuthState {
        val connected = preferences.getBoolean(keyConnected(provider), false)
        val cookie = SecureSecretStore.decryptOrPlain(preferences.getString(keyCookie(provider), null))
        val displayName = preferences.getString(keyDisplayName(provider), null)
        val hasCookie = !cookie.isNullOrBlank()
        return StreamingAuthState(
            kind = providerAuthKind(provider),
            connected = connected && hasCookie,
            accountDisplayName = displayName?.takeIf { it.isNotBlank() },
            statusMessage = if (connected && hasCookie) {
                "本地登录已保存"
            } else {
                "未登录"
            }
        )
    }

    override fun saveLogin(
        provider: StreamingProviderName,
        cookieHeader: String?,
        displayName: String?
    ): StreamingAuthState {
        val cookie = cookieHeader?.trim().orEmpty()
        if (cookie.isBlank()) {
            return authState(provider)
        }

        val editor = preferences.edit()
            .putBoolean(keyConnected(provider), true)
            .putString(keyCookie(provider), SecureSecretStore.encryptOrPlain(cookie))

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
            .commit()
        return authState(provider)
    }

    override fun cookieHeader(provider: StreamingProviderName): String? {
        return SecureSecretStore.decryptOrPlain(preferences.getString(keyCookie(provider), null))
            ?.takeIf { it.isNotBlank() }
    }

    override fun connected(provider: StreamingProviderName): Boolean {
        return preferences.getBoolean(keyConnected(provider), false) &&
            !SecureSecretStore.decryptOrPlain(preferences.getString(keyCookie(provider), null)).isNullOrBlank()
    }

    private fun keyConnected(provider: StreamingProviderName) = "connected:${provider.wireName}"
    private fun keyCookie(provider: StreamingProviderName) = "cookie:${provider.wireName}"
    private fun keyDisplayName(provider: StreamingProviderName) = "display:${provider.wireName}"

    companion object {
        const val PREFS_NAME: String = "streaming_local_auth"

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
