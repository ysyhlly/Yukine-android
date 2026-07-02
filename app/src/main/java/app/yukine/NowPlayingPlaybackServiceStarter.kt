package app.yukine

import android.content.Context
import android.content.Intent
import app.yukine.playback.EchoPlaybackService

internal class NowPlayingPlaybackServiceStarter(
    private val context: Context
) {
    fun startPlaybackService(action: String?) {
        val intent = Intent(context, EchoPlaybackService::class.java)
        if (action != null) {
            intent.action = action
        }
        context.startService(intent)
    }
}
