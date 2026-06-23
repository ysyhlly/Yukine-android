package app.yukine

import app.yukine.streaming.StreamingProviderName
import java.net.URI
import java.net.URLDecoder

internal class StreamingAuthCallbackBindings(
    private val streamingViewModel: StreamingViewModel,
    private val actionGateway: MainActivityStreamingActionGateway
) : StreamingAuthCallbackHandler {
    override fun handleAuthCallback(callbackUri: String?, cookieHeader: String?): Boolean {
        val uri = callbackUri
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it) }.getOrNull() }
            ?: return false
        if (uri.scheme != "echo-next" || uri.host != "streaming-auth") {
            return false
        }
        val providerValue = queryParameter(uri.rawQuery, "provider")
        val parsedProvider = providerValue
            ?.takeIf { it.isNotBlank() }
            ?.let(StreamingProviderName::fromWireName)
        val provider = parsedProvider ?: streamingViewModel.state.selectedProvider
        if (queryParameter(uri.rawQuery, "manualCookie") == "1") {
            actionGateway.openManualCookieImport(provider)
            streamingViewModel.clearStreamingAuthLaunch()
            return true
        }
        streamingViewModel.completeStreamingAuth(provider, uri.toString(), cookieHeader) { loggedInProvider ->
            actionGateway.onStreamingLoginSuccess(loggedInProvider)
        }
        streamingViewModel.clearStreamingAuthLaunch()
        return true
    }

    private fun queryParameter(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) {
            return null
        }
        return rawQuery
            .split("&")
            .asSequence()
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                val key = if (separator >= 0) part.substring(0, separator) else part
                if (decodeQueryValue(key) != name) {
                    null
                } else {
                    val value = if (separator >= 0) part.substring(separator + 1) else ""
                    decodeQueryValue(value)
                }
            }
            .firstOrNull()
    }

    private fun decodeQueryValue(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
