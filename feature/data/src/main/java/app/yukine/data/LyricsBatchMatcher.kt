package app.yukine.data

import app.yukine.model.LyricsDocument
import app.yukine.model.Track
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class LyricsImportCandidate(
    val sourceName: String,
    val document: LyricsDocument
)

data class LyricsBatchTrack(
    val recordingId: Long,
    val track: Track
)

sealed interface LyricsBatchMatch {
    data class Unique(val track: LyricsBatchTrack, val rule: String) : LyricsBatchMatch
    data class Ambiguous(val candidates: List<LyricsBatchTrack>) : LyricsBatchMatch
    data object Unmatched : LyricsBatchMatch
}

/**
 * Conservative matcher for directory imports. It deliberately has no fuzzy fallback: a result is
 * writable only when the union of all exact rules resolves to one canonical recording.
 */
object LyricsBatchMatcher {
    @JvmStatic
    fun match(candidate: LyricsImportCandidate, library: List<LyricsBatchTrack>): LyricsBatchMatch {
        val baseName = candidate.sourceName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.', "")
            .trim()
        val document = candidate.document
        val hits = linkedMapOf<Long, Pair<LyricsBatchTrack, String>>()

        fun add(rule: String, matches: Iterable<LyricsBatchTrack>) {
            matches.forEach { match -> hits.putIfAbsent(match.recordingId, match to rule) }
        }

        val normalizedBase = normalized(baseName)
        add(
            "audio-basename",
            library.filter { normalized(audioBaseName(it.track)) == normalizedBase }
        )
        if (document.title.isNotBlank() && document.artist.isNotBlank()) {
            add(
                "title-artist",
                library.filter {
                    normalized(it.track.title) == normalized(document.title) &&
                        normalized(it.track.artist) == normalized(document.artist)
                }
            )
        }
        if (document.title.isNotBlank() && document.album.isNotBlank()) {
            add(
                "title-album-duration",
                library.filter {
                    normalized(it.track.title) == normalized(document.title) &&
                        normalized(it.track.album) == normalized(document.album) &&
                        durationCompatible(documentDuration(document), it.track.durationMs)
                }
            )
        }
        add(
            "artist-title-filename",
            library.filter {
                val title = normalized(it.track.title)
                val artist = normalized(it.track.artist)
                normalizedBase == "$artist-$title" || normalizedBase == "$title-$artist"
            }
        )

        return when (hits.size) {
            0 -> LyricsBatchMatch.Unmatched
            1 -> hits.values.single().let { LyricsBatchMatch.Unique(it.first, it.second) }
            else -> LyricsBatchMatch.Ambiguous(hits.values.map(Pair<LyricsBatchTrack, String>::first))
        }
    }

    private fun audioBaseName(track: Track): String {
        val path = track.dataPath.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.', "")
    }

    private fun documentDuration(document: LyricsDocument): Long =
        document.tracks.asSequence()
            .flatMap { it.lines.asSequence() }
            .maxOfOrNull { it.endMs }
            ?: 0L

    private fun durationCompatible(first: Long, second: Long): Boolean {
        if (first <= 0L || second <= 0L) return false
        return abs(first - second) <= max(5_000L, (second * 0.02).toLong())
    }

    private fun normalized(value: String?): String = value.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_]+"), "")
        .replace(Regex("[–—－]+"), "-")
}
