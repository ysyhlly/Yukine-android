package app.yukine.streaming

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists streaming-provider login state captured locally on the device (typically via the
 * isolated WebView login flow), so that ECHO Next does not depend on a remote gateway just to
 * remember which providers the user has signed into.
 *
 * Each provider stores three pieces:
 * - whether the account is connected
 * - the cookie header captured at completion time (used to authorize follow-up calls)
 * - an optional human-readable display name surfaced in the UI
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
        val cookie = preferences.getString(keyCookie(provider), null)
        val displayName = preferences.getString(keyDisplayName(provider), null)
        val authKind = providerAuthKind(provider)
        return StreamingAuthState(
            kind = authKind,
            connected = connected && !cookie.isNullOrBlank(),
            accountDisplayName = displayName?.takeIf { it.isNotBlank() },
            statusMessage = if (connected && !cookie.isNullOrBlank()) {
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
        val editor = preferences.edit()
        if (cookie.isBlank()) {
            editor.remove(keyConnected(provider))
            editor.remove(keyCookie(provider))
            editor.remove(keyDisplayName(provider))
        } else {
            editor.putBoolean(keyConnected(provider), true)
            editor.putString(keyCookie(provider), cookie)
            if (!displayName.isNullOrBlank()) {
                editor.putString(keyDisplayName(provider), displayName.trim())
            }
        }
        editor.apply()
        return authState(provider)
    }

    override fun signOut(provider: StreamingProviderName): StreamingAuthState {
        preferences.edit()
            .remove(keyConnected(provider))
            .remove(keyCookie(provider))
            .remove(keyDisplayName(provider))
            .apply()
        return authState(provider)
    }

    override fun cookieHeader(provider: StreamingProviderName): String? {
        return preferences.getString(keyCookie(provider), null)?.takeIf { it.isNotBlank() }
    }

    override fun connected(provider: StreamingProviderName): Boolean {
        return preferences.getBoolean(keyConnected(provider), false) &&
            !preferences.getString(keyCookie(provider), null).isNullOrBlank()
    }

    private fun keyConnected(provider: StreamingProviderName) = "connected:${provider.wireName}"
    private fun keyCookie(provider: StreamingProviderName) = "cookie:${provider.wireName}"
    private fun keyDisplayName(provider: StreamingProviderName) = "display:${provider.wireName}"

    companion object {
        const val PREFS_NAME: String = "streaming_local_auth"

        /**
         * Resolve the auth kind ECHO Next would use for the given provider when the gateway is
         * unreachable. Cookie-style providers fall back to the in-app WebView; OAuth-style
         * providers fall back to Custom Tabs / external browser.
         */
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
