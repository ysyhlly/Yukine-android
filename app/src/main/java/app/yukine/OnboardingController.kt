package app.yukine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingUiState(
    val visible: Boolean = true,
    val audioPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val libraryScanCompleted: Boolean = false,
    val libraryScanInProgress: Boolean = false
)

internal class OnboardingController @JvmOverloads constructor(
    private val listener: Listener,
    private val scheduler: Scheduler = NoOpScheduler,
    private val libraryScanTimeoutMs: Long = DEFAULT_LIBRARY_SCAN_TIMEOUT_MS
) {
    interface Scheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)

        fun removeCallbacks(runnable: Runnable)
    }

    interface Listener {
        fun hasAudioPermission(): Boolean

        fun hasNotificationPermission(): Boolean

        fun requestNeededPermissions()

        fun loadLibrary(allowCachedFirst: Boolean)

        fun cancelLibraryLoad()

        fun onLibraryScanTimedOut()

        fun navigateToNetworkTabPage(page: String)

        fun renderSelectedTabAfterStateChange()

        fun openPlaylistM3uFilePicker()

        fun onboardingCompleted()
    }

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val libraryScanTimeout = Runnable {
        if (!state.value.visible || !state.value.libraryScanInProgress) {
            return@Runnable
        }
        publishState(libraryScanInProgress = false, libraryScanCompleted = false)
        listener.cancelLibraryLoad()
        listener.onLibraryScanTimedOut()
    }

    fun initialize(shouldShow: Boolean) {
        publishState(visible = shouldShow)
    }

    fun showOnboarding(): Boolean = state.value.visible

    fun libraryScanCompleted(): Boolean = state.value.libraryScanCompleted

    fun libraryScanInProgress(): Boolean = state.value.libraryScanInProgress

    fun onPermissionsChanged() {
        publishState()
    }

    fun finishOnboarding() {
        completeOnboarding { listener.renderSelectedTabAfterStateChange() }
    }

    fun openStreamingFromOnboarding() {
        completeOnboarding { listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING) }
    }

    fun scanLibraryFromOnboarding() {
        if (!listener.hasAudioPermission()) {
            listener.requestNeededPermissions()
            return
        }
        if (state.value.libraryScanInProgress) {
            return
        }
        publishState(libraryScanInProgress = true, libraryScanCompleted = false)
        scheduler.removeCallbacks(libraryScanTimeout)
        scheduler.postDelayed(libraryScanTimeout, libraryScanTimeoutMs)
        listener.loadLibrary(false)
    }

    fun importPlaylistFromOnboarding() {
        if (!canFinishOnboarding()) {
            return
        }
        listener.openPlaylistM3uFilePicker()
    }

    fun onLibraryScanResult(canScan: Boolean) {
        scheduler.removeCallbacks(libraryScanTimeout)
        if (state.value.visible && state.value.libraryScanInProgress) {
            publishState(libraryScanInProgress = false, libraryScanCompleted = canScan)
        }
    }

    fun release() {
        scheduler.removeCallbacks(libraryScanTimeout)
        if (state.value.libraryScanInProgress) {
            listener.cancelLibraryLoad()
        }
        publishState(libraryScanInProgress = false)
    }

    fun canFinishOnboarding(): Boolean {
        return listener.hasAudioPermission()
            && state.value.libraryScanCompleted
    }

    fun onboardingMissingSetupMessage(): String {
        val missing = ArrayList<String>()
        if (!listener.hasAudioPermission()) {
            missing.add("音频权限")
        }
        if (!state.value.libraryScanCompleted) {
            missing.add(if (state.value.libraryScanInProgress) "等待曲库扫描完成" else "扫描本地曲库")
        }
        return "完成后才能进入：" + missing.joinToString("、")
    }

    private fun completeOnboarding(afterComplete: Runnable?) {
        if (!canFinishOnboarding()) {
            return
        }
        publishState(visible = false)
        scheduler.removeCallbacks(libraryScanTimeout)
        listener.onboardingCompleted()
        afterComplete?.run()
    }

    private fun publishState(
        visible: Boolean = state.value.visible,
        libraryScanCompleted: Boolean = state.value.libraryScanCompleted,
        libraryScanInProgress: Boolean = state.value.libraryScanInProgress
    ) {
        mutableState.value = OnboardingUiState(
            visible = visible,
            audioPermissionGranted = listener.hasAudioPermission(),
            notificationPermissionGranted = listener.hasNotificationPermission(),
            libraryScanCompleted = libraryScanCompleted,
            libraryScanInProgress = libraryScanInProgress
        )
    }

    private companion object {
        const val DEFAULT_LIBRARY_SCAN_TIMEOUT_MS = 45_000L

        val NoOpScheduler = object : Scheduler {
            override fun postDelayed(runnable: Runnable, delayMs: Long) = Unit

            override fun removeCallbacks(runnable: Runnable) = Unit
        }
    }
}
