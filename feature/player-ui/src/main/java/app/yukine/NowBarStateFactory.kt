package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.LyricUiLine
import app.yukine.ui.NowBarArtworkState
import app.yukine.ui.NowBarErrorState
import app.yukine.ui.NowBarLabels
import app.yukine.ui.NowBarLyricsState
import app.yukine.ui.NowBarModesState
import app.yukine.ui.NowBarProgressState
import app.yukine.ui.NowBarState
import app.yukine.ui.NowBarTrackState
import app.yukine.ui.NowBarWaveformState
import app.yukine.ui.WaveformSamples

object NowBarStateFactory {
    @JvmStatic
    fun create(
        playbackState: PlaybackStateSnapshot,
        favoriteIds: Set<Long>,
        lyricsState: LyricsState?,
        languageMode: String
    ): NowBarState {
        val state = lyricsState ?: LyricsState()
        return create(
            playbackState = playbackState,
            favoriteIds = favoriteIds,
            languageMode = languageMode,
            lyrics = state.lines,
            lyricsStatus = LyricsStatusText.status(languageMode, state.statusKind, state.loadedLineCount),
            lyricsOffsetMs = state.offsetMs
        )
    }

    @JvmStatic
    fun create(
        playbackState: PlaybackStateSnapshot,
        favoriteIds: Set<Long>,
        languageMode: String
    ): NowBarState {
        return create(
            playbackState = playbackState,
            favoriteIds = favoriteIds,
            languageMode = languageMode,
            lyrics = emptyList(),
            lyricsStatus = "",
            lyricsOffsetMs = 0L
        )
    }

    private fun create(
        playbackState: PlaybackStateSnapshot,
        favoriteIds: Set<Long>,
        languageMode: String,
        lyrics: List<app.yukine.model.LyricsLine>,
        lyricsStatus: String,
        lyricsOffsetMs: Long
    ): NowBarState {
        val track = playbackState.currentTrack
        val labels = labels(languageMode)
        val error = NowBarErrorState(
            PlaybackErrorMessageLocalizer.localize(playbackState.errorMessage, languageMode)
        )
        if (track == null) {
            return NowBarState(
                track = NowBarTrackState(
                    title = AppLanguage.text(languageMode, "no.track.selected")
                ),
                progress = NowBarProgressState(),
                modes = NowBarModesState(
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode
                ),
                lyrics = NowBarLyricsState(
                    title = AppLanguage.text(languageMode, "lyrics"),
                    status = lyricsStatus
                ),
                labels = labels,
                artwork = NowBarArtworkState(),
                error = error
            )
        }
        val lyricRows = lyricRows(lyrics, playbackState.positionMs + lyricsOffsetMs)
        return NowBarState(
            track = NowBarTrackState(
                title = track.title,
                subtitle = subtitleWithSpec(track),
                trackId = track.id,
                contentUri = track.contentUri,
                dataPath = track.dataPath,
                canExpand = true
            ),
            progress = NowBarProgressState(
                elapsed = Track.formatDuration(playbackState.positionMs),
                duration = Track.formatDuration(playbackState.durationMs),
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                playing = playbackState.playing,
                waveform = NowBarWaveformState(
                    samples = WaveformSamples.of(playbackState.waveform.bars),
                    generatedBars = playbackState.waveform.generatedBars,
                    cachedProgress = playbackState.waveform.cachedProgress
                )
            ),
            modes = NowBarModesState(
                favorite = favoriteIds.contains(track.id),
                favoriteEnabled = true,
                shuffleEnabled = playbackState.shuffleEnabled,
                repeatMode = playbackState.repeatMode
            ),
            lyrics = NowBarLyricsState(
                title = AppLanguage.text(languageMode, "lyrics"),
                status = lyricsStatus,
                lines = lyricRows
            ),
            labels = labels,
            artwork = NowBarArtworkState(track.albumArtUri),
            error = error
        )
    }

    private fun labels(languageMode: String): NowBarLabels = NowBarLabels(
        favorite = AppLanguage.text(languageMode, "favorite"),
        favorited = AppLanguage.text(languageMode, "favorited"),
        shuffle = AppLanguage.text(languageMode, "shuffle"),
        inOrder = AppLanguage.text(languageMode, "in.order"),
        repeatOne = AppLanguage.text(languageMode, "repeat.one"),
        repeatAll = AppLanguage.text(languageMode, "repeat.all"),
        repeatOff = AppLanguage.text(languageMode, "repeat.off"),
        nowPlaying = AppLanguage.text(languageMode, "now.playing"),
        close = AppLanguage.text(languageMode, "close"),
        showLyrics = AppLanguage.text(languageMode, "show.lyrics"),
        showArtwork = AppLanguage.text(languageMode, "show.artwork"),
        noLyricsFound = AppLanguage.text(languageMode, "no.lyrics.found"),
        previous = AppLanguage.text(languageMode, "previous"),
        play = AppLanguage.text(languageMode, "play"),
        pause = AppLanguage.text(languageMode, "pause"),
        next = AppLanguage.text(languageMode, "next"),
        queue = AppLanguage.text(languageMode, "tab.queue"),
        more = AppLanguage.text(languageMode, "more"),
        addToPlaylist = AppLanguage.text(languageMode, "add.to.playlist"),
        playbackProgress = AppLanguage.text(languageMode, "playback.progress"),
        expandWaveform = AppLanguage.text(languageMode, "expand.playback.waveform"),
        playbackErrorTitle = AppLanguage.text(languageMode, "playback.error.title"),
        retry = AppLanguage.text(languageMode, "retry.playback"),
        dockLeft = AppLanguage.text(languageMode, "now.bar.dock.left"),
        dockRight = AppLanguage.text(languageMode, "now.bar.dock.right"),
        expandNowBar = AppLanguage.text(languageMode, "now.bar.expand"),
        dockTop = AppLanguage.text(languageMode, "now.bar.dock.top"),
        restoreBottom = AppLanguage.text(languageMode, "now.bar.restore.bottom"),
        collapseTopCloud = AppLanguage.text(languageMode, "now.bar.cloud.collapse"),
        showTopCloud = AppLanguage.text(languageMode, "now.bar.cloud.show"),
        expandTopCloud = AppLanguage.text(languageMode, "now.bar.cloud.expand"),
        compactTopCloud = AppLanguage.text(languageMode, "now.bar.cloud.compact")
    )

    private fun lyricRows(
        lyrics: List<app.yukine.model.LyricsLine>,
        positionMs: Long
    ): List<LyricUiLine> {
        if (lyrics.isEmpty()) {
            return emptyList()
        }
        val activeIndex = activeLyricIndex(lyrics, positionMs)
        return lyrics.mapIndexed { index, line -> LyricUiLine(line.text, index == activeIndex, line.timeMs) }
    }

    private fun activeLyricIndex(
        lyrics: List<app.yukine.model.LyricsLine>,
        positionMs: Long
    ): Int {
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
