package app.yukine.streaming

/**
 * Resolves a provider-specific login URL Yukine can open in its in-app WebView (for cookie
 * providers) or in Custom Tabs (for OAuth providers) when the streaming gateway is not connected.
 *
 * The URLs here intentionally point at each provider's official login page rather than at any
 * third-party API. Using them only causes the user's browser session inside the WebView to be
 * authenticated; Yukine then captures the resulting cookie and stores it in
 * [LocalStreamingAuthStore].
 */
object LocalStreamingLoginEndpoints {

    /**
     * @param redirectUri the deep link Yukine listens on (echo-next://streaming-auth). Cookie
     *   providers do not actually consume the redirect, but it is appended as a query parameter so
     *   that the [StreamingWebAuthActivity] fallback path can build a valid callback URI when the
     *   user dismisses the WebView early.
     */
    fun loginUrl(provider: StreamingProviderName, redirectUri: String?): String? {
        val safeRedirect = redirectUri?.trim().orEmpty()
        return when (provider) {
            StreamingProviderName.NETEASE -> attachCallback(
                "https://music.163.com/#/login",
                safeRedirect
            )
            StreamingProviderName.QQ_MUSIC -> attachCallback(
                "https://y.qq.com/portal/pop_login.html",
                safeRedirect
            )
            StreamingProviderName.KUGOU -> attachCallback(
                "https://www.kugou.com/",
                safeRedirect
            )
            StreamingProviderName.BILIBILI -> attachCallback(
                "https://passport.bilibili.com/login",
                safeRedirect
            )
            StreamingProviderName.YOUTUBE -> attachCallback(
                "https://accounts.google.com/ServiceLogin?service=youtube",
                safeRedirect
            )
            StreamingProviderName.SOUNDCLOUD -> attachCallback(
                "https://soundcloud.com/signin",
                safeRedirect
            )
            StreamingProviderName.SPOTIFY -> attachCallback(
                "https://accounts.spotify.com/login",
                safeRedirect
            )
            StreamingProviderName.TIDAL -> attachCallback(
                "https://login.tidal.com/",
                safeRedirect
            )
            StreamingProviderName.MOCK,
            StreamingProviderName.M3U8,
            StreamingProviderName.LUOXUE,
            StreamingProviderName.PLUGIN -> null
        }
    }

    /**
     * Best-effort host names used to read the captured cookie back from
     * [android.webkit.CookieManager] when the user finishes the WebView login.
     *
     * IMPORTANT: each provider writes its real session token on its *registrable* (top-level)
     * domain, not on the sub-domain that serves the visible login page. For example NetEase sets
     * `MUSIC_U` on `.163.com`, Bilibili sets `SESSDATA` on `.bilibili.com` (login page is
     * `passport.bilibili.com`), and QQ Music sets its keys on `.qq.com`. We therefore return every
     * candidate domain — the caller queries `CookieManager.getCookie(...)` for all of them and
     * merges the results, so HttpOnly session cookies on the parent domain are not missed.
     *
     * Note: `CookieManager.getCookie()` returns HttpOnly cookies as well (the HttpOnly flag only
     * blocks JavaScript's `document.cookie`, not the native cookie store), so the only thing that
     * makes a token "invisible" is querying the wrong domain — which this list fixes.
     */
    fun cookieDomainHints(provider: StreamingProviderName): List<String> {
        return when (provider) {
            StreamingProviderName.NETEASE -> listOf(
                "https://music.163.com/",
                "https://interface.music.163.com/",
                "https://m.music.163.com/",
                "https://login.163.com/",
                "https://www.163.com/",
                "https://163.com/"
            )
            StreamingProviderName.QQ_MUSIC -> listOf(
                "https://y.qq.com/portal/pop_login.html",
                "https://y.qq.com/",
                "https://m.y.qq.com/",
                "https://i.y.qq.com/n2/m/",
                "https://c.y.qq.com/",
                "https://i.y.qq.com/",
                "https://qq.com/",
                "https://www.qq.com/"
            )
            StreamingProviderName.KUGOU -> listOf(
                "https://www.kugou.com/",
                "https://kugou.com/",
                "https://m.kugou.com/"
            )
            StreamingProviderName.BILIBILI -> listOf(
                "https://www.bilibili.com/",
                "https://bilibili.com/",
                "https://passport.bilibili.com/",
                "https://api.bilibili.com/"
            )
            StreamingProviderName.YOUTUBE -> listOf(
                "https://www.youtube.com/",
                "https://youtube.com/",
                "https://accounts.google.com/",
                "https://google.com/"
            )
            StreamingProviderName.SOUNDCLOUD -> listOf(
                "https://soundcloud.com/",
                "https://secure.soundcloud.com/"
            )
            StreamingProviderName.SPOTIFY -> listOf(
                "https://www.spotify.com/",
                "https://accounts.spotify.com/",
                "https://open.spotify.com/",
                "https://spotify.com/"
            )
            StreamingProviderName.TIDAL -> listOf(
                "https://listen.tidal.com/",
                "https://login.tidal.com/",
                "https://tidal.com/"
            )
            else -> emptyList()
        }
    }

    /**
     * The cookie names that prove a logged-in session for each provider. Used to validate that a
     * captured cookie header actually contains the credential rather than just anonymous
     * tracking/visitor cookies, so the UI does not report a false "login success".
     */
    fun sessionTokenNames(provider: StreamingProviderName): List<String> {
        return when (provider) {
            StreamingProviderName.NETEASE -> listOf(
                "MUSIC_U",
                "MUSIC_A",
                "MUSIC_R",
                "MUSIC_R_T",
                "MUSIC_SNS"
            )
            StreamingProviderName.QQ_MUSIC -> listOf("qqmusic_key", "qm_keyst", "psrf_qqaccess_token")
            StreamingProviderName.KUGOU -> listOf("token", "t_token")
            StreamingProviderName.BILIBILI -> listOf("SESSDATA")
            StreamingProviderName.YOUTUBE -> listOf("SAPISID", "__Secure-3PAPISID", "SID")
            StreamingProviderName.SOUNDCLOUD -> listOf("oauth_token", "sc_anonymous_id")
            StreamingProviderName.SPOTIFY -> listOf("sp_dc", "sp_key")
            StreamingProviderName.TIDAL -> listOf("access_token", "tidal-session")
            else -> emptyList()
        }
    }

    /**
     * @return true if [capturedNames] contains at least one of the provider's session-token cookie
     *   names. Providers with no known token names (none configured) are treated as satisfied so we
     *   do not block login for them.
     */
    fun hasSessionToken(provider: StreamingProviderName, capturedNames: Collection<String>): Boolean {
        val required = sessionTokenNames(provider)
        if (required.isEmpty()) {
            return true
        }
        val present = capturedNames.map { it.trim() }.toHashSet()
        return required.any { present.contains(it) }
    }

    private fun attachCallback(base: String, redirect: String): String {
        if (redirect.isBlank()) {
            return base
        }
        val separator = if (base.contains("?")) "&" else "?"
        return base + separator + "echo_auth_callback=" + redirect
    }
}
