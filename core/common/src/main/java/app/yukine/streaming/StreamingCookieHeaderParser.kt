package app.yukine.streaming

object StreamingCookieHeaderParser {
    fun normalize(raw: String?): String {
        val input = raw?.trim().orEmpty()
        if (input.isBlank()) {
            return ""
        }
        val netscape = parseNetscapeCookieFile(input)
        if (netscape.isNotEmpty()) {
            return netscape
        }
        return input
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("; ")
    }

    /**
     * Merges two request-cookie headers by name. A non-blank refreshed value replaces the older
     * value, while an incomplete response never erases an existing credential.
     */
    fun merge(current: String?, refreshed: String?): String {
        val pairs = parseHeaderPairs(normalize(current))
        applyPairs(pairs, parseHeaderPairs(normalize(refreshed)))
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Applies HTTP `Set-Cookie` response headers to a request-cookie header.
     *
     * `Set-Cookie` attributes such as Path, Domain and Expires must not be copied into the
     * persisted request header, so only the leading name/value segment is used from each header.
     */
    fun mergeSetCookieHeaders(current: String?, setCookieHeaders: Collection<String>): String {
        val pairs = parseHeaderPairs(normalize(current))
        setCookieHeaders.forEach { header ->
            val firstPair = header.substringBefore(';').trim()
            applyPairs(pairs, parseHeaderPairs(firstPair))
        }
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun namesWithValue(raw: String?): Set<String> {
        return parseHeaderPairs(normalize(raw))
            .filterValues { it.isNotBlank() }
            .keys
    }

    private fun parseNetscapeCookieFile(input: String): String {
        val pairs = LinkedHashMap<String, String>()
        input.lineSequence()
            .map { it.trim() }
            // Netscape cookie exports encode HttpOnly domains as `#HttpOnly_<domain>`.
            // It is a cookie record rather than a comment and must survive manual imports.
            .filter { line ->
                line.isNotEmpty() &&
                    (!line.startsWith("#") || line.startsWith("#HttpOnly_", ignoreCase = true))
            }
            .forEach { line ->
                val fields = line.split('\t')
                if (fields.size < 7) {
                    return@forEach
                }
                val name = fields[5].trim()
                val value = fields.subList(6, fields.size).joinToString("\t").trim()
                if (name.isNotEmpty()) {
                    pairs[name] = value
                }
            }
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun parseHeaderPairs(header: String): LinkedHashMap<String, String> {
        val pairs = LinkedHashMap<String, String>()
        header.split(';').forEach { rawPair ->
            val pair = rawPair.trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) {
                return@forEach
            }
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name.isNotBlank()) {
                pairs[name] = value
            }
        }
        return pairs
    }

    private fun applyPairs(
        target: LinkedHashMap<String, String>,
        updates: LinkedHashMap<String, String>
    ) {
        updates.forEach { (name, value) ->
            val existingName = target.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            when {
                existingName == null && value.isNotBlank() -> target[name] = value
                existingName != null && value.isNotBlank() -> target[existingName] = value
            }
        }
    }
}
