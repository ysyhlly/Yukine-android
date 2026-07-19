package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoriteSyncPreferences(
    val autoSyncEnabled: Boolean = true,
    val syncOnForeground: Boolean = true,
    val periodicSyncEnabled: Boolean = false,
    val wifiOnly: Boolean = false,
    val propagateRemovals: Boolean = true,
    val confirmLowConfidence: Boolean = true,
    val intervalMinutes: Int = 30,
    val mode: FavoriteSyncMode = FavoriteSyncMode.REMOTE_TO_LOCAL,
    val selectedSourceKeys: Set<String> = emptySet()
)

enum class FavoriteSyncMode { REMOTE_TO_LOCAL }

data class FavoriteSyncSourceStatus(
    val sourceKey: String,
    val providerName: String,
    val sourceName: String,
    val selected: Boolean,
    val supported: Boolean,
    val loggedIn: Boolean,
    val statusText: String,
    val lastSyncAtMs: Long = 0L
)

data class FavoriteSyncDashboard(
    val lastSyncAtMs: Long = 0L,
    val pendingCount: Int = 0,
    val failureCount: Int = 0,
    val running: Boolean = false,
    val preferences: FavoriteSyncPreferences = FavoriteSyncPreferences(),
    val sources: List<FavoriteSyncSourceStatus> = emptyList()
)

interface FavoriteSyncController {
    val dashboard: StateFlow<FavoriteSyncDashboard>
    fun requestIncrementalSync()
    fun updatePreferences(transform: (FavoriteSyncPreferences) -> FavoriteSyncPreferences)
    fun setSourceEnabled(sourceKey: String, enabled: Boolean)
    fun clearSource(sourceKey: String)
}

class FavoriteSyncViewModel : ViewModel() {
    private val mutableDashboard = MutableStateFlow(FavoriteSyncDashboard())
    val dashboard: StateFlow<FavoriteSyncDashboard> = mutableDashboard.asStateFlow()
    private var controller: FavoriteSyncController? = null
    private var collectionJob: Job? = null

    fun bind(controller: FavoriteSyncController) {
        if (this.controller === controller) return
        this.controller = controller
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            controller.dashboard.collect { mutableDashboard.value = it }
        }
    }

    fun syncNow() = controller?.requestIncrementalSync()
    fun setAutoSync(value: Boolean) = update { it.copy(autoSyncEnabled = value) }
    fun setSyncOnForeground(value: Boolean) = update { it.copy(syncOnForeground = value) }
    fun setPeriodicSync(value: Boolean) = update { it.copy(periodicSyncEnabled = value) }
    fun setWifiOnly(value: Boolean) = update { it.copy(wifiOnly = value) }
    fun setPropagateRemovals(value: Boolean) = update { it.copy(propagateRemovals = value) }
    fun setConfirmLowConfidence(value: Boolean) = update { it.copy(confirmLowConfidence = value) }
    fun setSourceEnabled(sourceKey: String, enabled: Boolean) = controller?.setSourceEnabled(sourceKey, enabled)
    fun clearSource(sourceKey: String) = controller?.clearSource(sourceKey)

    private fun update(transform: (FavoriteSyncPreferences) -> FavoriteSyncPreferences) {
        controller?.updatePreferences(transform)
    }
}
