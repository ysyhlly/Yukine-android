package app.echo.next.streaming

import app.echo.next.model.Track

object StreamingTrackMatchPolicy {
    private val unknownValues = setOf(
        "\u672a\u77e5\u6b4c\u66f2",
        "\u672a\u77e5\u827a\u4eba",
        "\u672a\u77e5\u4e13\u8f91"
    )

    fun searchQuery(track: Track?): String {
        if (track == null) return ""
        val title = sanitize(track.title)
        val artist = sanitize(track.artist)
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> ""
        }
    }

    fun pickBestCandidate(local: Track?, candidates: List<StreamingTrack>): StreamingTrack? {
        if (local == null || candidates.isEmpty()) return null
        val titleNeedle = sanitize(local.title).lowercase()
        val artistNeedle = sanitize(local.artist).lowercase()
        val exact = candidates.firstOrNull { candidate ->
            sanitize(candidate.title).lowercase() == titleNeedle &&
                sanitize(candidate.artist).lowercase() == artistNeedle
        }
        if (exact != null) return exact
        val titleMatch = candidates.firstOrNull { candidate ->
            titleNeedle.isNotBlank() && sanitize(candidate.title).lowercase().contains(titleNeedle)
        }
        return titleMatch ?: candidates.first()
    }

    private fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        if (unknownValues.contains(trimmed)) return ""
        return trimmed
    }
}
