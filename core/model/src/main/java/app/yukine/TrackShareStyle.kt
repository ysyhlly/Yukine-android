package app.yukine

object TrackShareStyle {
    const val TEXT = "text"
    const val PLATFORM_CARD = "platform_card"
    const val CARD = "card"

    @JvmStatic
    fun defaultValue(): String = PLATFORM_CARD

    @JvmStatic
    fun normalize(style: String?): String =
        when (style?.trim()?.lowercase()) {
            PLATFORM_CARD -> PLATFORM_CARD
            "link", "platform", "netease", "music_card" -> PLATFORM_CARD
            CARD -> CARD
            TEXT -> TEXT
            else -> defaultValue()
        }

    @JvmStatic
    fun options(): List<String> = listOf(PLATFORM_CARD, TEXT, CARD)
}
