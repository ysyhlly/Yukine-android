package app.yukine.together

object TogetherRoomCode {
    private val pattern = Regex("^jun1[023456789acdefghjklmnpqrstuvwxyz]{20,80}$")

    fun normalize(value: String): String = value.trim().lowercase()

    fun isValid(value: String): Boolean = pattern.matches(normalize(value))
}
