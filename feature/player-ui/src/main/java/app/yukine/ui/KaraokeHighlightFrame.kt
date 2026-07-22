package app.yukine.ui

import androidx.compose.runtime.Immutable

@Immutable
data class KaraokeWordTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val startOffset: Int = 0,
    val endOffset: Int = 0
)

enum class KaraokeHighlightPhase {
    COMPLETED,
    CURRENT,
    UPCOMING
}

@Immutable
data class KaraokeHighlightRange(
    val start: Int,
    val end: Int,
    val phase: KaraokeHighlightPhase,
    val progress: Float
)

@Immutable
data class KaraokeHighlightFrame(
    val text: String,
    val ranges: List<KaraokeHighlightRange>
)

fun karaokeHighlightFrame(
    text: String,
    words: List<KaraokeWordTiming>,
    positionMs: Long
): KaraokeHighlightFrame {
    if (text.isEmpty() || words.isEmpty()) return KaraokeHighlightFrame(text, emptyList())

    var searchFrom = 0
    val ranges = buildList(words.size) {
        words.forEach { word ->
            if (word.text.isEmpty() || word.endMs < word.startMs) return@forEach
            val bounds = explicitBounds(text, word, searchFrom)
                ?: fallbackBounds(text, word.text, searchFrom)
                ?: return@forEach
            val (start, end) = bounds
            val phase = when {
                positionMs >= word.endMs -> KaraokeHighlightPhase.COMPLETED
                positionMs >= word.startMs -> KaraokeHighlightPhase.CURRENT
                else -> KaraokeHighlightPhase.UPCOMING
            }
            val progress = when (phase) {
                KaraokeHighlightPhase.COMPLETED -> 1f
                KaraokeHighlightPhase.UPCOMING -> 0f
                KaraokeHighlightPhase.CURRENT -> {
                    val duration = word.endMs - word.startMs
                    if (duration <= 0L) 1f
                    else ((positionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
                }
            }
            add(KaraokeHighlightRange(start, end, phase, progress))
            searchFrom = end
        }
    }
    return KaraokeHighlightFrame(text, ranges)
}

internal fun activeLyricIndex(lines: List<LyricUiLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        if (lines[middle].timeMs <= positionMs) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

@Immutable
internal data class LyricsFollowState(val following: Boolean = true) {
    fun pauseForUserScroll(): LyricsFollowState = copy(following = false)
    fun resume(): LyricsFollowState = copy(following = true)
}

internal fun LyricUiWord.asKaraokeWordTiming(): KaraokeWordTiming = KaraokeWordTiming(
    text = text,
    startMs = startMs,
    endMs = endMs,
    startOffset = startOffset,
    endOffset = endOffset
)

private fun explicitBounds(
    text: String,
    word: KaraokeWordTiming,
    searchFrom: Int
): Pair<Int, Int>? {
    if (word.startOffset < searchFrom || word.endOffset <= word.startOffset || word.endOffset > text.length) {
        return null
    }
    return if (text.substring(word.startOffset, word.endOffset) == word.text) {
        word.startOffset to word.endOffset
    } else {
        null
    }
}

private fun fallbackBounds(text: String, word: String, searchFrom: Int): Pair<Int, Int>? {
    val start = text.indexOf(word, searchFrom)
    if (start < 0) return null
    return start to (start + word.length).coerceAtMost(text.length)
}
