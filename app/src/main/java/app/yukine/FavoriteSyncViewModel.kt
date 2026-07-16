package app.yukine

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lifecycle scheduling stays outside MainActivity and outside Compose. */
internal class FavoriteSyncRuntimeOwner(
    lifecycle: Lifecycle,
    private val coordinator: FavoriteSyncCoordinator,
    private val viewModel: FavoriteSyncViewModel,
    refreshLibrary: Runnable?
) : DefaultLifecycleObserver {
    private val lifecycle = lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: Job? = null

    init {
        lifecycle.addObserver(this)
        coordinator.start(refreshLibrary)
        viewModel.bind(coordinator)
    }

    override fun onStart(owner: LifecycleOwner) {
        val preferences = coordinator.dashboard.value.preferences
        if (preferences.autoSyncEnabled && preferences.syncOnForeground) coordinator.requestIncrementalSync()
        periodicJob?.cancel()
        if (preferences.autoSyncEnabled && preferences.periodicSyncEnabled) {
            periodicJob = scope.launch {
                while (true) {
                    delay(preferences.intervalMinutes.coerceAtLeast(15) * 60_000L)
                    coordinator.requestIncrementalSync()
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun close() {
        periodicJob?.cancel()
        lifecycle.removeObserver(this)
        scope.cancel()
        coordinator.close()
    }
}
