package app.echo.next

import app.echo.next.model.Track
import app.echo.next.playback.EchoPlaybackService
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.NowBarState

internal object NowBarStateFactory {
    @JvmStatic
    fun create(
        playbackState: PlaybackStateSnapshot,
        favoriteIds: Set<Long>,
        languageMode: String
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
                repeatLabel = repeatLabel(playbackState.repeatMode, languageMode),
                repeatOffLabel = AppLanguage.text(languageMode, "repeat.off"),
                repeatMode = playbackState.repeatMode,
                albumArtUri = null,
                trackId = -1L,
                contentUri = null,
                dataPath = "",
                waveformBars = FloatArray(0),
                waveformGeneratedBars = 0,
                waveformCachedProgress = 0f
            )
        }
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
            repeatLabel = repeatLabel(playbackState.repeatMode, languageMode),
            repeatOffLabel = AppLanguage.text(languageMode, "repeat.off"),
            repeatMode = playbackState.repeatMode,
            albumArtUri = track.albumArtUri,
            trackId = track.id,
            contentUri = track.contentUri,
            dataPath = track.dataPath,
            waveformBars = playbackState.waveform.bars,
            waveformGeneratedBars = playbackState.waveform.generatedBars,
            waveformCachedProgress = playbackState.waveform.cachedProgress
        )
    }

    private fun repeatLabel(repeatMode: Int, languageMode: String): String {
        if (repeatMode == EchoPlaybackService.REPEAT_ONE) {
            return AppLanguage.text(languageMode, "repeat.one")
        }
        if (repeatMode == EchoPlaybackService.REPEAT_OFF) {
            return AppLanguage.text(languageMode, "repeat.off")
        }
        return AppLanguage.text(languageMode, "repeat.all")
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
