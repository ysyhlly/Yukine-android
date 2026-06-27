package app.yukine.playback.manager

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession

internal class PlaybackSessionManager(
    private val service: MediaLibraryService,
    private val playerFactory: () -> Player,
    private val callback: MediaLibrarySession.Callback,
    private val sessionActivityProvider: () -> PendingIntent
) {
    private var sessionPlayer: Player? = null
    private var mediaSession: MediaLibrarySession? = null

    fun session(): MediaLibrarySession? = mediaSession

    @OptIn(UnstableApi::class)
    fun bind() {
        val nextPlayer = playerFactory()
        sessionPlayer = nextPlayer
        val currentSession = mediaSession
        if (currentSession == null) {
            mediaSession = MediaLibrarySession.Builder(service, nextPlayer, callback)
                .setId(CHANNEL_ID)
                .setSessionActivity(sessionActivityProvider())
                .build()
        } else {
            currentSession.setPlayer(nextPlayer)
        }
    }

    fun refreshPlayer() {
        val currentSession = mediaSession ?: return
        val nextPlayer = playerFactory()
        sessionPlayer = nextPlayer
        currentSession.setPlayer(nextPlayer)
    }

    fun release() {
        mediaSession?.release()
        mediaSession = null
        sessionPlayer = null
    }

    companion object {
        const val CHANNEL_ID = "echo_next_playback"
    }
}
