package app.yukine.ui

import androidx.compose.runtime.Immutable

@Immutable
data class LyricUiLine(
    val text: String,
    val active: Boolean,
    val timeMs: Long = 0L,
    val endTimeMs: Long = timeMs + 3_000L,
    val translation: String = "",
    val romanization: String = "",
    val words: List<LyricUiWord> = emptyList()
)

@Immutable
data class LyricUiWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val startOffset: Int = 0,
    val endOffset: Int = 0
)

internal fun adjustedLyricPositionMs(playbackPositionMs: Long, offsetMs: Long): Long =
    (playbackPositionMs + offsetMs).coerceAtLeast(0L)

internal fun playbackPositionForLyricMs(lyricPositionMs: Long, offsetMs: Long): Long =
    (lyricPositionMs - offsetMs).coerceAtLeast(0L)

internal fun LyricUiLine.isActiveAt(positionMs: Long): Boolean =
    positionMs in timeMs until endTimeMs.coerceAtLeast(timeMs + 1L)

internal fun LyricUiWord.isActiveAt(positionMs: Long): Boolean =
    positionMs in startMs until endMs.coerceAtLeast(startMs + 1L)

internal fun LyricUiWord.progressAt(positionMs: Long): Float =
    when {
        positionMs >= endMs -> 1f
        positionMs <= startMs -> 0f
        else -> (positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)
    }

internal fun LyricUiWord.textBounds(lineText: String, searchFrom: Int): Pair<Int, Int>? {
    if (
        startOffset >= searchFrom &&
        endOffset > startOffset &&
        endOffset <= lineText.length &&
        lineText.substring(startOffset, endOffset) == text
    ) {
        return startOffset to endOffset
    }
    val start = lineText.indexOf(text, searchFrom)
    if (start < 0) return null
    return start to (start + text.length).coerceAtMost(lineText.length)
}
