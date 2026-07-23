package app.yukine.diagnostics

/** Removes credentials and user-identifying values before diagnostics reach disk or an export. */
object DiagnosticRedactor {
    private val authorization = Regex(
        "(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?[^\\s,;]+"
    )
    private val cookie = Regex("(?i)((?:set-cookie|cookie)\\s*[:=]\\s*)[^\\r\\n]+")
    private val querySecret = Regex(
        "(?i)([?&](?:access_token|refresh_token|token|api[_-]?key|password|secret)=)[^&#\\s]+"
    )
    private val networkUrl = Regex("(?i)\\b(?:https?|rtsp)://[^\\s\\\"'<>]+")
    private val mediaValue = Regex(
        "(?i)\\b(title|dataPath|uri|track)(\\s*[:=]\\s*)" +
            "(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,;\\r\\n]+)"
    )
    private val namedValue = Regex(
        "(?i)\\b(access[_-]?token|refresh[_-]?token|token|api[_-]?key|password|passwd|secret|" +
            "accountId|username|trackId|providerTrackId|deviceName|productName)" +
            "(\\s*[:=]\\s*)[\\\"']?[^,;\\s&\\\"']+"
    )
    private val privatePath = Regex(
        "(?i)(?:/storage/emulated/\\d+|/sdcard|/data/user/\\d+|/data/data)/[^\\s\\\"']+"
    )

    @JvmStatic
    fun redact(value: String): String {
        if (value.isEmpty()) return value
        return value
            .replace(authorization) { "${it.groupValues[1]}<redacted>" }
            .replace(cookie) { "${it.groupValues[1]}<redacted>" }
            .replace(networkUrl) { redactNetworkUrl(it.value) }
            .replace(querySecret) { "${it.groupValues[1]}<redacted>" }
            .replace(mediaValue) {
                "${it.groupValues[1]}${it.groupValues[2]}<redacted>"
            }
            .replace(namedValue) {
                "${it.groupValues[1]}${it.groupValues[2]}<redacted>"
            }
            .replace(privatePath, "<private-path>")
    }

    private fun redactNetworkUrl(url: String): String {
        val queryIndex = url.indexOf('?').takeIf { it >= 0 }
        val fragmentIndex = url.indexOf('#').takeIf { it >= 0 }
        val sensitiveIndex = listOfNotNull(queryIndex, fragmentIndex).minOrNull() ?: return url
        val marker = if (queryIndex != null && queryIndex == sensitiveIndex) '?' else '#'
        val replacement = if (marker == '?') {
            "?<redacted-url-params>"
        } else {
            "#<redacted-url-fragment>"
        }
        return url.substring(0, sensitiveIndex) + replacement
    }
}
