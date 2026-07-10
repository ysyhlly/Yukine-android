package app.yukine

import android.webkit.CookieManager
import app.yukine.streaming.LocalStreamingLoginEndpoints
import app.yukine.streaming.StreamingCookieHeaderParser
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingWebCookieSessionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android/WebView implementation of the streaming module's isolated-cookie boundary. */
class AndroidStreamingWebCookieSessionSource : StreamingWebCookieSessionSource {
    override suspend fun readCookieHeader(provider: StreamingProviderName): String? {
        return withContext(Dispatchers.Main.immediate) {
            collectCookieHeader(provider)
        }
    }

    override suspend fun clearSession(provider: StreamingProviderName) {
        withContext(Dispatchers.Main.immediate) {
            StreamingWebAuthActivity.prepareStreamingAuthCookieStore()
            val cookieManager = CookieManager.getInstance()
            val candidates = cookieCandidates(provider)
            val names = linkedSetOf<String>().apply {
                addAll(LocalStreamingLoginEndpoints.sessionTokenNames(provider))
                candidates.forEach { candidate ->
                    addAll(StreamingCookieHeaderParser.namesWithValue(cookieManager.getCookie(candidate)))
                }
            }
            candidates.forEach { candidate ->
                names.forEach { name ->
                    cookieManager.setCookie(candidate, "$name=; Max-Age=0; Path=/")
                }
            }
            cookieManager.flush()
        }
    }

    companion object {
        /**
         * Reads and merges cookies across the login host and its parent domains. This method is
         * intentionally reusable by [StreamingWebAuthActivity] so capture and background session
         * maintenance apply exactly the same credential rules.
         */
        fun collectCookieHeader(
            provider: StreamingProviderName?,
            extraCandidates: Collection<String> = emptyList()
        ): String? {
            StreamingWebAuthActivity.prepareStreamingAuthCookieStore()
            val cookieManager = CookieManager.getInstance()
            val candidates = linkedSetOf<String>().apply {
                addAll(extraCandidates.filter { it.isNotBlank() })
                provider?.let { addAll(cookieCandidates(it)) }
            }
            var header = ""
            candidates.forEach { candidate ->
                val raw = cookieManager.getCookie(candidate)?.takeIf { it.isNotBlank() } ?: return@forEach
                header = StreamingCookieHeaderParser.merge(header, raw)
            }
            if (header.isBlank()) {
                return null
            }
            val namesWithValue = StreamingCookieHeaderParser.namesWithValue(header)
            return if (provider == null || LocalStreamingLoginEndpoints.hasSessionToken(provider, namesWithValue)) {
                header
            } else {
                null
            }
        }

        private fun cookieCandidates(provider: StreamingProviderName): List<String> {
            return LocalStreamingLoginEndpoints.cookieDomainHints(provider)
        }
    }
}
