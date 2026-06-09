package app.echo.next

import app.echo.next.model.LyricsLine
import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.LyricUiLine
import app.echo.next.ui.NowPlayingUiState

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
            lyricRows.add(LyricUiLine(line.text, index == activeIndex))
        }
        return NowPlayingUiState(
            AppLanguage.text(languageMode, "tab.now"),
            track.title,
            subtitleWithSpec(track),
            AppLanguage.text(languageMode, "tab.queue"),
            "${playbackState.currentIndex + 1} / ${playbackState.queueSize}",
            AppLanguage.text(languageMode, "duration"),
            Track.formatDuration(playbackState.durationMs),
            playbackState.errorMessage,
            track.albumArtUri,
            AppLanguage.text(languageMode, "lyrics"),
            lyricsStatus,
            lyricRows
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
}
