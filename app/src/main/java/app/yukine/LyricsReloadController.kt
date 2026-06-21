package app.yukine

import app.yukine.model.Track

internal class LyricsReloadController(
    private val listener: Listener
) {
    interface Listener {
        fun currentTrack(): Track?

        fun providerTrackId(track: Track?): String

        fun loadLyrics(track: Track?, providerTrackId: String)

        fun noTrackSelectedStatus(): String

        fun reloadingLyricsStatus(): String

        fun setStatus(status: String)
    }

    fun reloadCurrentLyrics() {
        val track = listener.currentTrack()
        val providerTrackId = track?.let { listener.providerTrackId(it) }.orEmpty()
        listener.loadLyrics(track, providerTrackId)
        listener.setStatus(
            if (track == null) {
                listener.noTrackSelectedStatus()
            } else {
                listener.reloadingLyricsStatus()
            }
        )
    }
}
