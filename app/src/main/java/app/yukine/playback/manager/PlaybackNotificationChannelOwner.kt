package app.yukine.playback.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal class PlaybackNotificationChannelOwner(
    private val context: Context
) {
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = CHANNEL_DESCRIPTION
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "echo_next_playback"
        private const val CHANNEL_NAME = "Playback"
        private const val CHANNEL_DESCRIPTION = "Yukine playback controls"
    }
}
