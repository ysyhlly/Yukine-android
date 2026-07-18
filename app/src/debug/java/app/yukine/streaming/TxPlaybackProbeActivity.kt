package app.yukine.streaming

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Activity entry point for devices whose vendor broadcast queue delays debug probe receivers. */
@UnstableApi
class TxPlaybackProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "正在验证 LX/TX 音源…"
            textSize = 18f
            setPadding(48, 72, 48, 48)
        })
        val trackId = intent.getStringExtra("trackId").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val artist = intent.getStringExtra("artist").orEmpty()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val resolved = TxPlaybackProbeReceiver().probe(
                    applicationContext,
                    trackId,
                    title,
                    artist
                )
                if (resolved != null) verifyMutedMedia3Playback(resolved)
            } finally {
                finish()
            }
        }
    }

    private suspend fun verifyMutedMedia3Playback(resolved: StreamingResolvedPlayback) {
        val source = resolved.source
        val startedAt = System.nanoTime()
        val outcome = runCatching {
            withContext(Dispatchers.Main) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(source.headers)
                val dataSourceFactory = DefaultDataSource.Factory(this@TxPlaybackProbeActivity, httpFactory)
                val player = ExoPlayer.Builder(this@TxPlaybackProbeActivity)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
                try {
                    player.volume = 0f
                    player.setMediaItem(MediaItem.fromUri(source.url))
                    player.prepare()
                    player.play()
                    withTimeout(12_000L) {
                        while (!player.isPlaying) {
                            player.playerError?.let { throw it }
                            delay(100L)
                        }
                    }
                    delay(1_500L)
                    player.playerError?.let { throw it }
                    check(player.currentPosition > 0L) { "Media3 播放位置没有前进" }
                    player.currentPosition
                } finally {
                    player.release()
                }
            }
        }
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
        Log.i(
            LOG_TAG,
            "stage=media3_playback success=${outcome.isSuccess} durationMs=$durationMs " +
                "positionMs=${outcome.getOrNull() ?: 0L} provider=${source.provider.wireName} " +
                "error=${outcome.exceptionOrNull()?.message.orEmpty()}"
        )
    }

    private companion object {
        const val LOG_TAG = "TxPlaybackProbe"
    }
}
