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

    private fun parseNetscapeCookieFile(input: String): String {
        val pairs = LinkedHashMap<String, String>()
        input.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
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
}
