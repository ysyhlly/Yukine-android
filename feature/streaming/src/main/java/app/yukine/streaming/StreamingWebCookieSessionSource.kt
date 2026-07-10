package app.yukine.streaming

/**
 * Platform boundary for the isolated WebView cookie jar. The streaming module owns the session
 * policy, while the app module owns Android WebView access and its main-thread requirements.
 */
interface StreamingWebCookieSessionSource {
    suspend fun readCookieHeader(provider: StreamingProviderName): String?

    suspend fun clearSession(provider: StreamingProviderName)
}

object NoopStreamingWebCookieSessionSource : StreamingWebCookieSessionSource {
    override suspend fun readCookieHeader(provider: StreamingProviderName): String? = null

    override suspend fun clearSession(provider: StreamingProviderName) = Unit
}
