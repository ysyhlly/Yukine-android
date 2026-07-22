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
            .replace(querySecret) { "${it.groupValues[1]}<redacted>" }
            .replace(namedValue) {
                "${it.groupValues[1]}${it.groupValues[2]}<redacted>"
            }
            .replace(privatePath, "<private-path>")
    }
}
