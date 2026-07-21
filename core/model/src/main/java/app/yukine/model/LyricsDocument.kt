package app.yukine.model

enum class LyricsTrackRole {
    PRIMARY,
    TRANSLATION,
    ROMANIZATION
}

data class LyricWord @JvmOverloads constructor(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val startOffset: Int = 0,
    val endOffset: Int = 0
) {
    init {
        require(startMs >= 0L)
        require(endMs >= startMs)
    }
}

data class LyricLine @JvmOverloads constructor(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    init {
        require(startMs >= 0L)
        require(endMs >= startMs)
    }
}

data class LyricsTrack @JvmOverloads constructor(
    val role: LyricsTrackRole,
    val languageTag: String = "",
    val lines: List<LyricLine> = emptyList()
)

data class LyricsDocument @JvmOverloads constructor(
    val sourceName: String = "",
    val format: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val tracks: List<LyricsTrack> = emptyList()
) {
    fun isEmpty(): Boolean = tracks.none { it.lines.isNotEmpty() }

    fun track(role: LyricsTrackRole): LyricsTrack? =
        tracks.firstOrNull { it.role == role && it.lines.isNotEmpty() }

    fun primaryOrFirstTrack(): LyricsTrack? =
        track(LyricsTrackRole.PRIMARY) ?: tracks.firstOrNull { it.lines.isNotEmpty() }

    fun primaryLegacyLines(): List<LyricsLine> =
        primaryOrFirstTrack()?.lines.orEmpty().map { LyricsLine(it.startMs, it.text) }

    companion object {
        @JvmStatic
        fun empty(): LyricsDocument = LyricsDocument()

        @JvmStatic
        fun fromLegacy(
            lines: List<LyricsLine>?,
            sourceName: String = "",
            format: String = "legacy"
        ): LyricsDocument {
            val safeLines = lines.orEmpty()
            if (safeLines.isEmpty()) return empty()
            val primaryLines = mutableListOf<LyricLine>()
            val translationLines = mutableListOf<LyricLine>()
            safeLines.forEachIndexed { index, line ->
                val nextStart = safeLines.getOrNull(index + 1)?.timeMs
                val endMs = nextStart?.coerceAtLeast(line.timeMs) ?: (line.timeMs + 3_000L)
                val newlineIdx = line.text.indexOf('\n')
                if (newlineIdx > 0 && newlineIdx < line.text.length - 1) {
                    val primaryText = line.text.substring(0, newlineIdx)
                    val translationText = line.text.substring(newlineIdx + 1)
                    primaryLines += LyricLine(line.timeMs, endMs, primaryText)
                    if (translationText.isNotBlank()) {
                        translationLines += LyricLine(line.timeMs, endMs, translationText)
                    }
                } else {
                    primaryLines += LyricLine(line.timeMs, endMs, line.text)
                }
            }
            val tracks = mutableListOf(LyricsTrack(LyricsTrackRole.PRIMARY, lines = primaryLines))
            if (translationLines.isNotEmpty()) {
                tracks += LyricsTrack(LyricsTrackRole.TRANSLATION, lines = translationLines)
            }
            return LyricsDocument(
                sourceName = sourceName,
                format = format,
                tracks = tracks
            )
        }
    }
}
