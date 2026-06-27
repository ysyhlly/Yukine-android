package app.yukine.playback.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

internal class PlaybackNoisyReceiverManager(
    private val registrar: Registrar,
    private val actions: Actions
) {
    interface Registrar {
        fun register(receiver: BroadcastReceiver, filter: IntentFilter)
        fun unregister(receiver: BroadcastReceiver)
    }

    interface Actions {
        fun pauseIfPlaying()
    }

    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                actions.pauseIfPlaying()
            }
        }
    }

    fun register() {
        if (registered) {
            return
        }
        registrar.register(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        registered = true
    }

    fun unregister() {
        if (!registered) {
            return
        }
        try {
            registrar.unregister(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver may already have been unregistered by the system.
        }
        registered = false
    }
}
