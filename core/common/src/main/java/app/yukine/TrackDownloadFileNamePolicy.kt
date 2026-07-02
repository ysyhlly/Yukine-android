package app.yukine

import app.yukine.model.Track

object TrackDownloadFileNamePolicy {
    private val invalidFileNameChars = Regex("[\\\\/:*?\"<>|\\r\\n\\t]+")
    private val whitespace = Regex("\\s+")
    private val trailingDotsAndSpaces = Regex("[. ]+$")
    private val unknownArtists = setOf(
        "未知艺人",
        "未知歌手",
        "unknown",
        "Unknown Artist",
        "<unknown>",
        "null"
    )
    private val unknownTitles = setOf(
        "未知歌曲",
        "未知标题",
        "unknown",
        "Unknown Title",
        "<unknown>",
        "null"
    )
    private const val MAX_BASE_LENGTH = 120

    fun audioFileName(track: Track, extension: String): String =
        "${baseName(track)}.${cleanExtension(extension)}"

    fun artworkFileName(track: Track, extension: String): String =
        "${baseName(track)} - cover.${cleanExtension(extension)}"

    fun baseName(track: Track): String {
        val artist = cleanPart(track.artist).takeUnless { it.isUnknownArtist() }
        val title = cleanPart(track.title).takeUnless { it.isUnknownTitle() }
        val fallback = "Yukine Track ${track.id}"
        val base = when {
            !artist.isNullOrBlank() && !title.isNullOrBlank() && !artist.equals(title, ignoreCase = true) ->
                "$artist - $title"
            !title.isNullOrBlank() -> title
            !artist.isNullOrBlank() -> artist
            else -> fallback
        }
        return safeFilePart(base, fallback)
    }

    private fun cleanPart(value: String): String =
        value
            .replace(invalidFileNameChars, " ")
            .replace(whitespace, " ")
            .trim()

    private fun safeFilePart(value: String, fallback: String): String =
        value
            .replace(invalidFileNameChars, "_")
            .replace(whitespace, " ")
            .replace(trailingDotsAndSpaces, "")
            .trim()
            .take(MAX_BASE_LENGTH)
            .replace(trailingDotsAndSpaces, "")
            .ifBlank { fallback }

    private fun cleanExtension(extension: String): String =
        extension
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .takeIf { it.length in 2..5 }
            ?: "mp3"

    private fun String.isUnknownArtist(): Boolean =
        unknownArtists.any { equals(it, ignoreCase = true) }

    private fun String.isUnknownTitle(): Boolean =
        unknownTitles.any { equals(it, ignoreCase = true) }
}
