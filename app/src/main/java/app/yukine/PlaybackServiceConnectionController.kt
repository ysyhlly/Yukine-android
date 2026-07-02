package app.yukine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.state.PlaybackStateListener

internal class PlaybackServiceConnectionController(
    private val context: Context,
    private val playbackStateListener: PlaybackStateListener,
    private val listener: Listener
) {
    interface Listener {
        fun onPlaybackServiceConnected(service: PlaybackServiceHostPort)

        fun onPlaybackServiceDisconnected()
    }

    private var service: PlaybackServiceHostPort? = null
    private var bound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val nextService: PlaybackServiceHostPort = (binder as EchoPlaybackService.LocalBinder).service
            if (service != null && service !== nextService) {
                service?.unregisterListener(playbackStateListener)
            }
            service = nextService
            bound = true
            nextService.registerListener(playbackStateListener)
            listener.onPlaybackServiceConnected(nextService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }

        override fun onBindingDied(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }

        override fun onNullBinding(name: ComponentName) {
            clearServiceReference()
            listener.onPlaybackServiceDisconnected()
        }
    }

    fun bind() {
        context.bindService(Intent(context, EchoPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun release() {
        service?.unregisterListener(playbackStateListener)
        if (bound) {
            context.unbindService(connection)
        }
        service = null
        bound = false
    }

    fun isBound(): Boolean {
        return bound
    }

    private fun clearServiceReference() {
        val disconnectedService = service
        service = null
        bound = false
        disconnectedService?.unregisterListener(playbackStateListener)
    }
}
