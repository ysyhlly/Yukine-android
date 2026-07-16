package app.yukine.streaming

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in true-device probe for an LX/TX track that previously timed out in the playback UI.
 *
 * Run with `-e txTrackId <id>`; ordinary connected test suites skip this network probe.
 */
@RunWith(AndroidJUnit4::class)
class TxPlaybackResolutionInstrumentedTest {
    @Test
    fun configuredLxTxSourceResolvesWithinRepositoryBudget() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rawTrackId = InstrumentationRegistry.getArguments()
            .getString(ARG_TX_TRACK_ID)
            .orEmpty()
            .trim()
        assumeTrue("Pass -e $ARG_TX_TRACK_ID to run the LX/TX device probe", rawTrackId.isNotBlank())

        val providerTrackId = rawTrackId
            .removePrefix("luoxue:")
            .let { value -> if (value.startsWith("tx:")) value else "tx:$value" }
        val sourceStore = LuoxueSourceStore(instrumentation.targetContext)
        val enabledSources = sourceStore.load().count { it.enabled }
        assertTrue("The device has no enabled imported LX source", enabledSources > 0)

        val telemetry = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val repository = StreamingRepository(
            gateway = RemoteStreamingGateway(
                endpointBaseUrl = "",
                luoxueSourceStore = sourceStore
            ),
            playbackTelemetry = StreamingPlaybackTelemetry { event -> telemetry += event }
        )
        val startedAt = System.nanoTime()
        val result = runCatching {
            repository.resolvePlaybackTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = providerTrackId,
                forceRefresh = true
            )
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
        Log.i(
            LOG_TAG,
            "result success=${result.isSuccess} elapsedMs=$elapsedMs enabledSources=$enabledSources " +
                "trackSuffix=${providerTrackId.takeLast(6)}"
        )

        val resolved = result.getOrThrow()
        assertEquals(StreamingProviderName.LUOXUE, resolved.source.provider)
        assertTrue(resolved.source.url.startsWith("http://") || resolved.source.url.startsWith("https://"))
        assertTrue("LX/TX resolution exceeded the 12 second repository budget", elapsedMs <= 12_500L)
    }

    private companion object {
        const val ARG_TX_TRACK_ID = "txTrackId"
        const val LOG_TAG = "TxPlaybackProbe"
    }
}
