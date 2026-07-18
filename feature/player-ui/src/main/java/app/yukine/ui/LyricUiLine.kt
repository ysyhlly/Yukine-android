package app.yukine.ui

data class LyricUiLine(
    val text: String,
    val active: Boolean,
    val timeMs: Long = 0L,
    val endTimeMs: Long = timeMs + 3_000L,
    val translation: String = "",
    val romanization: String = "",
    val words: List<LyricUiWord> = emptyList()
)

data class LyricUiWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)
