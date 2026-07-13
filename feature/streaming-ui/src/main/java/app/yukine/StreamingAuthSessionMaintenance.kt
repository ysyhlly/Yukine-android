package app.yukine

import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingProviderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns foreground streaming-session maintenance policy.
 *
 * The owner deliberately knows nothing about UI loading/error state. It only throttles duplicate
 * provider checks, publishes successful auth snapshots, and refreshes diagnostics after the pass.
 */
class StreamingAuthSessionMaintenance(
    private val scope: CoroutineScope,
    private val refresh: suspend (StreamingProviderName) -> StreamingAuthState,
    private val diagnostics: () -> StreamingGatewayDiagnostics,
    private val onAuthState: (StreamingProviderName, StreamingAuthState) -> Unit,
    private val onDiagnostics: (StreamingGatewayDiagnostics) -> Unit,
    private val providers: List<StreamingProviderName> = listOf(
        StreamingProviderName.NETEASE,
        StreamingProviderName.QQ_MUSIC
    )
) {
    private val inFlight = ConcurrentHashMap.newKeySet<StreamingProviderName>()

    fun maintain(): Job = scope.launch {
        providers.forEach { provider ->
            if (!inFlight.add(provider)) {
                return@forEach
            }
            try {
                runCatching { refresh(provider) }
                    .onSuccess { authState -> onAuthState(provider, authState) }
            } finally {
                inFlight.remove(provider)
            }
        }
        onDiagnostics(diagnostics())
    }
}
