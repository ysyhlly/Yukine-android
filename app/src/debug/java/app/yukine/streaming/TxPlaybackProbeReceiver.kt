package app.yukine.streaming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Debug-only true-device probe; it resolves a transient URL and never writes canonical state. */
class TxPlaybackProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawTrackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty().trim()
        if (rawTrackId.isBlank()) {
            Log.e(LOG_TAG, "missing trackId")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                probe(
                    context.applicationContext,
                    rawTrackId,
                    intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun probe(
        context: Context,
        rawTrackId: String,
        title: String = "",
        artist: String = ""
    ): StreamingResolvedPlayback? {
        val providerTrackId = rawTrackId
            .removePrefix("luoxue:")
            .let { value -> if (value.startsWith("tx:")) value else "tx:$value" }
        val sourceStore = LuoxueSourceStore(context)
        val enabledSources = sourceStore.load().count { it.enabled }
        val telemetry = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val repository = StreamingRepository(
            gateway = RemoteStreamingGateway(
                endpointBaseUrl = "",
                luoxueSourceStore = sourceStore
            ),
            playbackTelemetry = StreamingPlaybackTelemetry { event -> telemetry += event }
        )
        val startedAt = System.nanoTime()
        Log.i(
            LOG_TAG,
            "start enabledSources=$enabledSources hasTitle=${title.isNotBlank()} " +
                "trackSuffix=${providerTrackId.takeLast(6)}"
        )
        val result = runCatching {
            withTimeout(14_000L) {
                repository.resolvePlaybackTrack(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = providerTrackId,
                    metadata = title.trim().takeIf(String::isNotBlank)?.let { cleanTitle ->
                        StreamingTrack(
                            provider = StreamingProviderName.LUOXUE,
                            providerTrackId = providerTrackId,
                            title = cleanTitle,
                            artist = artist.trim()
                        )
                    },
                    forceRefresh = true
                )
            }
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        telemetry.forEach { event ->
            Log.i(
                LOG_TAG,
                "stage=${event.stage.wireName} provider=${event.provider.wireName} " +
                    "path=${event.resolutionPath?.wireName.orEmpty()} durationMs=${event.durationMs} " +
                    "success=${event.success} timedOut=${event.timedOut} error=${event.errorCode}"
            )
        }
        val source = result.getOrNull()?.source
        Log.i(
            LOG_TAG,
            "result success=${source != null} provider=${source?.provider?.wireName.orEmpty()} " +
                "elapsedMs=$elapsedMs enabledSources=$enabledSources trackSuffix=${providerTrackId.takeLast(6)} " +
                "error=${result.exceptionOrNull()?.message.orEmpty()}"
        )
        return result.getOrNull()
    }

    private companion object {
        const val EXTRA_TRACK_ID = "trackId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val LOG_TAG = "TxPlaybackProbe"
    }
}
