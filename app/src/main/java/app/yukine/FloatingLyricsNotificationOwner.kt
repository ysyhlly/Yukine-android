package app.yukine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

internal class FloatingLyricsNotificationOwner(
    private val context: Context
) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.floating_lyrics_notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.floating_lyrics_notification_waiting)
                setShowBadge(false)
            }
        )
    }

    fun build(presentation: FloatingLyricsPresentation): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_echo)
            .setContentTitle(context.getString(R.string.floating_lyrics_notification_title))
            .setContentText(context.getString(contentText(presentation)))
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(Notification.PRIORITY_LOW)

        when (presentation) {
            FloatingLyricsPresentation.HiddenByUser -> addAction(
                builder,
                R.drawable.ic_notif_play,
                R.string.floating_lyrics_notification_show,
                FloatingLyricsService.ACTION_SHOW,
                REQUEST_SHOW
            )
            is FloatingLyricsPresentation.Visible -> {
                if (presentation.interaction == FloatingLyricsInteraction.ClickThrough) {
                    addAction(
                        builder,
                        R.drawable.ic_notif_stop,
                        R.string.floating_lyrics_disable_click_through,
                        FloatingLyricsService.ACTION_UNLOCK,
                        REQUEST_UNLOCK
                    )
                } else {
                    addAction(
                        builder,
                        R.drawable.ic_notif_stop,
                        R.string.floating_lyrics_notification_hide,
                        FloatingLyricsService.ACTION_HIDE_SESSION,
                        REQUEST_HIDE
                    )
                }
            }
            FloatingLyricsPresentation.WaitingForLyrics -> Unit
        }
        return builder.build()
    }

    fun notify(presentation: FloatingLyricsPresentation) {
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, build(presentation))
    }

    private fun contentText(presentation: FloatingLyricsPresentation): Int = when (presentation) {
        FloatingLyricsPresentation.WaitingForLyrics ->
            R.string.floating_lyrics_notification_waiting
        FloatingLyricsPresentation.HiddenByUser ->
            R.string.floating_lyrics_notification_hidden
        is FloatingLyricsPresentation.Visible -> {
            if (presentation.interaction == FloatingLyricsInteraction.ClickThrough) {
                R.string.floating_lyrics_notification_click_through
            } else {
                R.string.floating_lyrics_notification_active
            }
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(FloatingLyricsService.EXTRA_OPEN_SETTINGS, true)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun addAction(
        builder: Notification.Builder,
        iconRes: Int,
        titleRes: Int,
        action: String,
        requestCode: Int
    ) {
        val pendingIntent = PendingIntent.getService(
            context,
            requestCode,
            Intent(context, FloatingLyricsService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, iconRes),
                    context.getString(titleRes),
                    pendingIntent
                ).build()
            )
        } else {
            @Suppress("DEPRECATION")
            builder.addAction(iconRes, context.getString(titleRes), pendingIntent)
        }
    }

    companion object {
        const val CHANNEL_ID = "echo_floating_lyrics"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_OPEN_APP = 200
        private const val REQUEST_SHOW = 201
        private const val REQUEST_HIDE = 202
        private const val REQUEST_UNLOCK = 203
    }
}
