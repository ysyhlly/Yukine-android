package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingControllerTest {
    @Test
    fun permissionChangesPublishIntoTheExistingShellState() {
        val listener = FakeListener(audioPermission = false, notificationPermission = false)
        val controller = OnboardingController(listener)
        controller.initialize(true)

        listener.audioPermission = true
        listener.notificationPermission = true
        controller.onPermissionsChanged()

        assertTrue(controller.state.value.audioPermissionGranted)
        assertTrue(controller.state.value.notificationPermissionGranted)
        assertTrue(controller.state.value.visible)
    }

    @Test
    fun canFinishAfterAudioPermissionAndLibraryScanEvenWhenNotificationPermissionIsMissing() {
        val listener = FakeListener(
            audioPermission = true,
            notificationPermission = false
        )
        val controller = OnboardingController(listener)

        controller.scanLibraryFromOnboarding()
        controller.onLibraryScanResult(true)

        assertTrue(controller.canFinishOnboarding())
        assertEquals(1, listener.loadLibraryCalls)
    }

    @Test
    fun failedScanClearsInProgressSoOnboardingCanRetry() {
        val listener = FakeListener(audioPermission = true)
        val controller = OnboardingController(listener)

        controller.scanLibraryFromOnboarding()
        assertTrue(controller.libraryScanInProgress())

        controller.onLibraryScanResult(false)

        assertFalse(controller.libraryScanInProgress())
        assertFalse(controller.libraryScanCompleted())
        assertTrue(controller.onboardingMissingSetupMessage().contains("扫描本地曲库"))
    }

    @Test
    fun scanWithoutAudioPermissionRequestsPermissionWithoutStartingLibraryLoad() {
        val listener = FakeListener(audioPermission = false)
        val controller = OnboardingController(listener)

        controller.scanLibraryFromOnboarding()

        assertEquals(1, listener.permissionRequests)
        assertEquals(0, listener.loadLibraryCalls)
        assertFalse(controller.state.value.audioPermissionGranted)
    }

    @Test
    fun hungScanTimeoutCancelsLoadAndRestoresRetryableState() {
        val listener = FakeListener(audioPermission = true)
        val scheduler = FakeScheduler()
        val controller = OnboardingController(listener, scheduler, 1_000L)

        controller.scanLibraryFromOnboarding()
        controller.scanLibraryFromOnboarding()
        scheduler.runPending()

        assertEquals(1, listener.loadLibraryCalls)
        assertEquals(1, listener.cancelLibraryLoadCalls)
        assertEquals(1, listener.scanTimedOutCalls)
        assertFalse(controller.libraryScanInProgress())
        assertFalse(controller.libraryScanCompleted())
        assertTrue(controller.onboardingMissingSetupMessage().contains("扫描本地曲库"))
        assertFalse(controller.state.value.libraryScanInProgress)
    }

    private class FakeListener(
        var audioPermission: Boolean = false,
        var notificationPermission: Boolean = true
    ) : OnboardingController.Listener {
        var permissionRequests = 0
        var loadLibraryCalls = 0
        var cancelLibraryLoadCalls = 0
        var scanTimedOutCalls = 0
        var completedCalls = 0

        override fun hasAudioPermission(): Boolean = audioPermission

        override fun hasNotificationPermission(): Boolean = notificationPermission

        override fun requestNeededPermissions() {
            permissionRequests++
        }

        override fun loadLibrary(allowCachedFirst: Boolean) {
            loadLibraryCalls++
        }

        override fun cancelLibraryLoad() {
            cancelLibraryLoadCalls++
        }

        override fun onLibraryScanTimedOut() {
            scanTimedOutCalls++
        }

        override fun navigateToNetworkTabPage(page: String) {
        }

        override fun openPlaylistM3uFilePicker() {
        }

        override fun onboardingCompleted() {
            completedCalls++
        }
    }

    private class FakeScheduler : OnboardingController.Scheduler {
        private var pending: Runnable? = null

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pending = runnable
        }

        override fun removeCallbacks(runnable: Runnable) {
            if (pending === runnable) {
                pending = null
            }
        }

        fun runPending() {
            val runnable = pending
            pending = null
            runnable?.run()
        }
    }
}
