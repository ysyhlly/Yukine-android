package app.yukine.together

object TogetherRoomCode {
    private const val bodyClass = "023456789acdefghjklmnpqrstuvwxyz"
    private val pattern = Regex("^jun1[$bodyClass]{20,80}$")
    private val embedded = Regex("jun1[$bodyClass]{20,80}")

    fun normalize(value: String): String = value.trim().lowercase()

    fun isValid(value: String): Boolean = pattern.matches(normalize(value))

    /**
     * Pulls a room code from free-form paste text (share messages, extra spaces, surrounding copy).
     * Returns null when no jun1… token is found.
     */
    fun extractFromText(value: String): String? {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return null
        if (isValid(normalized)) return normalized
        return embedded.find(normalized)?.value?.takeIf(::isValid)
    }
}
