package app.yukine

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

        fun mountNavHostShell()

        fun loadLibrary(allowCachedFirst: Boolean)

        fun cancelLibraryLoad()

        fun onLibraryScanTimedOut()

        fun navigateToNetworkTabPage(page: String)

        fun renderSelectedTabAfterStateChange()

        fun openPlaylistM3uFilePicker()

        fun onboardingCompleted()
    }

    private var visible: Boolean = true
    private var libraryScanCompleted: Boolean = false
    private var libraryScanInProgress: Boolean = false
    private val libraryScanTimeout = Runnable {
        if (!visible || !libraryScanInProgress) {
            return@Runnable
        }
        libraryScanInProgress = false
        libraryScanCompleted = false
        listener.cancelLibraryLoad()
        listener.onLibraryScanTimedOut()
        listener.mountNavHostShell()
    }

    fun initialize(shouldShow: Boolean) {
        visible = shouldShow
    }

    fun showOnboarding(): Boolean = visible

    fun libraryScanCompleted(): Boolean = libraryScanCompleted

    fun libraryScanInProgress(): Boolean = libraryScanInProgress

    fun finishOnboarding() {
        completeOnboarding { listener.renderSelectedTabAfterStateChange() }
    }

    fun openStreamingFromOnboarding() {
        completeOnboarding { listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING) }
    }

    fun scanLibraryFromOnboarding() {
        if (!listener.hasAudioPermission()) {
            listener.requestNeededPermissions()
            listener.mountNavHostShell()
            return
        }
        if (libraryScanInProgress) {
            return
        }
        libraryScanInProgress = true
        libraryScanCompleted = false
        listener.mountNavHostShell()
        scheduler.removeCallbacks(libraryScanTimeout)
        scheduler.postDelayed(libraryScanTimeout, libraryScanTimeoutMs)
        listener.loadLibrary(false)
    }

    fun importPlaylistFromOnboarding() {
        if (!canFinishOnboarding()) {
            listener.mountNavHostShell()
            return
        }
        listener.openPlaylistM3uFilePicker()
    }

    fun onLibraryScanResult(canScan: Boolean) {
        scheduler.removeCallbacks(libraryScanTimeout)
        if (visible && libraryScanInProgress) {
            libraryScanInProgress = false
            libraryScanCompleted = canScan
        }
    }

    fun release() {
        scheduler.removeCallbacks(libraryScanTimeout)
        if (libraryScanInProgress) {
            listener.cancelLibraryLoad()
        }
        libraryScanInProgress = false
    }

    fun canFinishOnboarding(): Boolean {
        return listener.hasAudioPermission()
            && libraryScanCompleted
    }

    fun onboardingMissingSetupMessage(): String {
        val missing = ArrayList<String>()
        if (!listener.hasAudioPermission()) {
            missing.add("音频权限")
        }
        if (!libraryScanCompleted) {
            missing.add(if (libraryScanInProgress) "等待曲库扫描完成" else "扫描本地曲库")
        }
        return "完成后才能进入：" + missing.joinToString("、")
    }

    private fun completeOnboarding(afterComplete: Runnable?) {
        if (!canFinishOnboarding()) {
            listener.mountNavHostShell()
            return
        }
        visible = false
        scheduler.removeCallbacks(libraryScanTimeout)
        listener.onboardingCompleted()
        listener.mountNavHostShell()
        afterComplete?.run()
    }

    private companion object {
        const val DEFAULT_LIBRARY_SCAN_TIMEOUT_MS = 45_000L

        val NoOpScheduler = object : Scheduler {
            override fun postDelayed(runnable: Runnable, delayMs: Long) = Unit

            override fun removeCallbacks(runnable: Runnable) = Unit
        }
    }
}
