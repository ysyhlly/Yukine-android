package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.LyricUiLine
import app.yukine.ui.NowBarState

internal object NowBarStateFactory {
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
        if (track == null) {
            return NowBarState(
                title = AppLanguage.text(languageMode, "no.track.selected"),
                subtitle = "",
                elapsed = Track.formatDuration(0),
                duration = Track.formatDuration(0),
                positionMs = 0L,
                durationMs = 0L,
                playing = false,
                favorite = false,
                favoriteEnabled = false,
                canExpand = false,
                shuffleEnabled = playbackState.shuffleEnabled,
                favoriteLabel = AppLanguage.text(languageMode, "favorite"),
                favoritedLabel = AppLanguage.text(languageMode, "favorited"),
                shuffleLabel = AppLanguage.text(languageMode, "shuffle"),
                inOrderLabel = AppLanguage.text(languageMode, "in.order"),
                repeatOneLabel = AppLanguage.text(languageMode, "repeat.one"),
                repeatAllLabel = AppLanguage.text(languageMode, "repeat.all"),
                repeatOffLabel = AppLanguage.text(languageMode, "repeat.off"),
                nowPlayingLabel = AppLanguage.text(languageMode, "now.playing"),
                repeatMode = playbackState.repeatMode,
                albumArtUri = null,
                trackId = -1L,
                contentUri = null,
                dataPath = "",
                waveformBars = FloatArray(0),
                waveformGeneratedBars = 0,
                waveformCachedProgress = 0f,
                lyricsTitle = AppLanguage.text(languageMode, "lyrics"),
                lyricsStatus = lyricsStatus,
                closeLabel = AppLanguage.text(languageMode, "close"),
                showLyricsLabel = AppLanguage.text(languageMode, "show.lyrics"),
                showArtworkLabel = AppLanguage.text(languageMode, "show.artwork"),
                noLyricsFoundLabel = AppLanguage.text(languageMode, "no.lyrics.found"),
                previousLabel = AppLanguage.text(languageMode, "previous"),
                playLabel = AppLanguage.text(languageMode, "play"),
                pauseLabel = AppLanguage.text(languageMode, "pause"),
                nextLabel = AppLanguage.text(languageMode, "next"),
                queueLabel = AppLanguage.text(languageMode, "tab.queue"),
                moreLabel = AppLanguage.text(languageMode, "more"),
                addToPlaylistLabel = AppLanguage.text(languageMode, "add.to.playlist"),
                playbackProgressLabel = AppLanguage.text(languageMode, "playback.progress"),
                expandWaveformLabel = AppLanguage.text(languageMode, "expand.playback.waveform"),
                playbackErrorTitle = AppLanguage.text(languageMode, "playback.error.title"),
                playbackErrorMessage = PlaybackErrorMessageLocalizer.localize(playbackState.errorMessage, languageMode),
                retryLabel = AppLanguage.text(languageMode, "retry.playback"),
                lyrics = emptyList()
            )
        }
        val lyricRows = lyricRows(lyrics, playbackState.positionMs + lyricsOffsetMs)
        return NowBarState(
            title = track.title,
            subtitle = subtitleWithSpec(track),
            elapsed = Track.formatDuration(playbackState.positionMs),
            duration = Track.formatDuration(playbackState.durationMs),
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            playing = playbackState.playing,
            favorite = favoriteIds.contains(track.id),
            favoriteEnabled = true,
            canExpand = true,
            shuffleEnabled = playbackState.shuffleEnabled,
            favoriteLabel = AppLanguage.text(languageMode, "favorite"),
            favoritedLabel = AppLanguage.text(languageMode, "favorited"),
            shuffleLabel = AppLanguage.text(languageMode, "shuffle"),
            inOrderLabel = AppLanguage.text(languageMode, "in.order"),
            repeatOneLabel = AppLanguage.text(languageMode, "repeat.one"),
            repeatAllLabel = AppLanguage.text(languageMode, "repeat.all"),
            repeatOffLabel = AppLanguage.text(languageMode, "repeat.off"),
            nowPlayingLabel = AppLanguage.text(languageMode, "now.playing"),
            repeatMode = playbackState.repeatMode,
            albumArtUri = track.albumArtUri,
            trackId = track.id,
            contentUri = track.contentUri,
            dataPath = track.dataPath,
            waveformBars = playbackState.waveform.bars,
            waveformGeneratedBars = playbackState.waveform.generatedBars,
            waveformCachedProgress = playbackState.waveform.cachedProgress,
            lyricsTitle = AppLanguage.text(languageMode, "lyrics"),
            lyricsStatus = lyricsStatus,
            closeLabel = AppLanguage.text(languageMode, "close"),
            showLyricsLabel = AppLanguage.text(languageMode, "show.lyrics"),
            showArtworkLabel = AppLanguage.text(languageMode, "show.artwork"),
            noLyricsFoundLabel = AppLanguage.text(languageMode, "no.lyrics.found"),
            previousLabel = AppLanguage.text(languageMode, "previous"),
            playLabel = AppLanguage.text(languageMode, "play"),
            pauseLabel = AppLanguage.text(languageMode, "pause"),
            nextLabel = AppLanguage.text(languageMode, "next"),
            queueLabel = AppLanguage.text(languageMode, "tab.queue"),
            moreLabel = AppLanguage.text(languageMode, "more"),
            addToPlaylistLabel = AppLanguage.text(languageMode, "add.to.playlist"),
            playbackProgressLabel = AppLanguage.text(languageMode, "playback.progress"),
            expandWaveformLabel = AppLanguage.text(languageMode, "expand.playback.waveform"),
            playbackErrorTitle = AppLanguage.text(languageMode, "playback.error.title"),
            playbackErrorMessage = PlaybackErrorMessageLocalizer.localize(playbackState.errorMessage, languageMode),
            retryLabel = AppLanguage.text(languageMode, "retry.playback"),
            lyrics = lyricRows
        )
    }

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
