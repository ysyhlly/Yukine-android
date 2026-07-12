package app.yukine

import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingProviderName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingAuthSessionMaintenanceTest {
    @Test
    fun refreshesConfiguredProvidersAndPublishesDiagnostics() = runTest {
        val refreshed = mutableListOf<StreamingProviderName>()
        val authStates = mutableListOf<Pair<StreamingProviderName, StreamingAuthState>>()
        val diagnostics = mutableListOf<StreamingGatewayDiagnostics>()
        val maintenance = StreamingAuthSessionMaintenance(
            scope = this,
            refresh = { provider ->
                refreshed += provider
                StreamingAuthState(connected = true)
            },
            diagnostics = { StreamingGatewayDiagnostics(totalRequests = 2, cacheHits = 1) },
            onAuthState = { provider, state -> authStates += provider to state },
            onDiagnostics = { diagnostics += it }
        )

        maintenance.maintain()
        advanceUntilIdle()

        assertEquals(
            listOf(StreamingProviderName.NETEASE, StreamingProviderName.QQ_MUSIC),
            refreshed
        )
        assertEquals(2, authStates.size)
        assertEquals(StreamingGatewayDiagnostics(totalRequests = 2, cacheHits = 1), diagnostics.single())
    }

    @Test
    fun overlappingMaintenanceDoesNotRefreshSameProviderTwice() = runTest {
        val gate = CompletableDeferred<Unit>()
        val refreshed = mutableListOf<StreamingProviderName>()
        val maintenance = StreamingAuthSessionMaintenance(
            scope = this,
            refresh = { provider ->
                refreshed += provider
                gate.await()
                StreamingAuthState()
            },
            diagnostics = { StreamingGatewayDiagnostics() },
            onAuthState = { _, _ -> },
            onDiagnostics = { }
        )

        maintenance.maintain()
        runCurrent()
        maintenance.maintain()
        runCurrent()
        assertEquals(
            listOf(StreamingProviderName.NETEASE, StreamingProviderName.QQ_MUSIC),
            refreshed
        )

        gate.complete(Unit)
        advanceUntilIdle()
    }
}
