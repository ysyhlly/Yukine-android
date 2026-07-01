package app.yukine.playback.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import java.util.function.BooleanSupplier

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

    companion object {
        @JvmStatic
        fun actionsFromPlaybackState(
            playbackStateProvider: BooleanSupplier?,
            pauseAction: Runnable?
        ): Actions = object : Actions {
            override fun pauseIfPlaying() {
                if (playbackStateProvider?.asBoolean == true) {
                    pauseAction?.run()
                }
            }
        }
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
