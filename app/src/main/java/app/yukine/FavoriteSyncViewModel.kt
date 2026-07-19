package app.yukine

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lifecycle scheduling stays outside MainActivity and outside Compose. */
internal class FavoriteSyncRuntimeOwner(
    context: Context,
    lifecycle: Lifecycle,
    private val coordinator: FavoriteSyncCoordinator,
    private val viewModel: FavoriteSyncViewModel,
    refreshLibrary: Runnable?
) : DefaultLifecycleObserver {
    private val lifecycle = lifecycle
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        lifecycle.addObserver(this)
        coordinator.start(refreshLibrary)
        viewModel.bind(coordinator)
        scope.launch {
            coordinator.dashboard
                .map { it.preferences }
                .distinctUntilChanged()
                .collect { FavoriteSyncBackgroundScheduler.update(appContext, it) }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val preferences = coordinator.dashboard.value.preferences
        val lastSyncAtMs = coordinator.dashboard.value.lastSyncAtMs
        if (shouldRunFavoriteSyncOnForeground(preferences, lastSyncAtMs, System.currentTimeMillis())) {
            coordinator.requestIncrementalSync()
        }
    }

    fun close() {
        lifecycle.removeObserver(this)
        scope.cancel()
        coordinator.close()
    }

}

internal const val FAVORITE_SYNC_FOREGROUND_STALE_MS = 30L * 60L * 1000L

internal fun shouldRunFavoriteSyncOnForeground(
    preferences: FavoriteSyncPreferences,
    lastSyncAtMs: Long,
    nowMs: Long
): Boolean {
    if (!preferences.autoSyncEnabled || !preferences.syncOnForeground) return false
    return lastSyncAtMs <= 0L || nowMs - lastSyncAtMs >= FAVORITE_SYNC_FOREGROUND_STALE_MS
}
