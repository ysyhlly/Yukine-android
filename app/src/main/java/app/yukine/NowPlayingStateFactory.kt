package app.yukine

import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.ui.LyricUiLine
import app.yukine.ui.NowPlayingUiState
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object NowPlayingStateFactory {
    @JvmStatic
    fun create(
        playbackState: PlaybackStateSnapshot,
        lyrics: List<LyricsLine>,
        lyricsStatus: String,
        lyricsOffsetMs: Long,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): NowPlayingUiState? {
        val track = playbackState.currentTrack ?: return null
        val lyricRows = ArrayList<LyricUiLine>()
        val activeIndex = activeLyricIndex(lyrics, playbackState.positionMs + lyricsOffsetMs)
        for (index in lyrics.indices) {
            val line = lyrics[index]
            lyricRows.add(LyricUiLine(line.text, index == activeIndex, line.timeMs))
        }
        return NowPlayingUiState(
            AppLanguage.text(languageMode, "tab.now"),
            track.title,
            subtitleWithSpec(track),
            AppLanguage.text(languageMode, "tab.queue"),
            "${playbackState.currentIndex + 1} / ${playbackState.queueSize}",
            AppLanguage.text(languageMode, "duration"),
            Track.formatDuration(playbackState.durationMs),
            PlaybackErrorMessageLocalizer.localize(playbackState.errorMessage, languageMode),
            track.albumArtUri,
            AppLanguage.text(languageMode, "lyrics"),
            lyricsStatus,
            lyricRows,
            track.artist,
            track.album,
            track.audioSpecSummary(),
            songInfo(track),
            sourceInfo(track)
        )
    }

    private fun activeLyricIndex(lyrics: List<LyricsLine>, positionMs: Long): Int {
        var active = -1
        for (index in lyrics.indices) {
            if (lyrics[index].timeMs <= positionMs) {
                active = index
            } else {
                break
            }
        }
        return active
    }

    private fun subtitleWithSpec(track: Track): String {
        val spec = track.audioSpecSummary()
        if (spec.isBlank()) {
            return track.subtitle()
        }
        val subtitle = track.subtitle()
        return if (subtitle.isBlank()) spec else "$subtitle / $spec"
    }

    private fun songInfo(track: Track): String {
        return encodedMeta(track, "desc")
            ?: listOfNotNull(
                track.album.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
                track.audioSpecSummary().takeIf { it.isNotBlank() }?.let { "规格：$it" }
            ).joinToString("\n")
    }

    private fun sourceInfo(track: Track): String {
        val provider = StreamingDataPathMetadata.provider(track.dataPath)
        val source = provider?.wireName?.let { "播放源：$it" }
        val lyricSources = encodedMeta(track, "lyrics")
            ?.takeIf { it.isNotBlank() }
            ?.let { "歌词源：$it" }
        val playbackSources = encodedMeta(track, "sources")
            ?.takeIf { it.isNotBlank() }
            ?.let { "可用播放源：$it" }
        return listOfNotNull(source, lyricSources, playbackSources).joinToString("\n")
    }

    private fun encodedMeta(track: Track, key: String): String? {
        val marker = "?"
        val index = track.dataPath.indexOf(marker)
        if (index < 0 || index >= track.dataPath.length - 1) {
            return null
        }
        val query = track.dataPath.substring(index + 1)
        return query.split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
            }
            .firstOrNull { it.first == key }
            ?.second
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.takeIf { it.isNotBlank() }
    }
}
