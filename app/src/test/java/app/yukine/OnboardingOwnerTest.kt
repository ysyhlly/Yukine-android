package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingOwnerTest {
    @Test
    fun initializesFromPersistenceAndCompletesIntoNetworkNavigation() {
        val fixture = Fixture(audioPermission = true, shouldShow = true)
        fixture.owner.initialize()

        fixture.owner.scanLibrary()
        fixture.owner.onLibraryScanResult(true)
        fixture.owner.openStreaming()

        assertFalse(fixture.owner.state.value.visible)
        assertEquals(listOf("load:false", "completed", "navigate:${NetworkPage.Streaming}"), fixture.calls)
    }

    @Test
    fun deniedPermissionRequestsPermissionAndDoesNotStartScan() {
        val fixture = Fixture(audioPermission = false, shouldShow = true)
        fixture.owner.initialize()

        fixture.owner.scanLibrary()

        assertEquals(listOf("request-permissions"), fixture.calls)
        assertFalse(fixture.owner.state.value.libraryScanInProgress)
    }

    @Test
    fun timeoutCancelsScanAndPublishesLocalizedStatus() {
        val fixture = Fixture(audioPermission = true, shouldShow = true)
        fixture.owner.initialize()

        fixture.owner.scanLibrary()
        fixture.scheduler.runPending()

        assertEquals(
            listOf(
                "load:false",
                "cancel-load",
                "status:${AppLanguage.text(AppLanguage.MODE_ENGLISH, "library.scan.timeout")}"),
            fixture.calls
        )
        assertFalse(fixture.owner.state.value.libraryScanInProgress)
    }

    @Test
    fun savedCompletionStartsWithOnboardingHidden() {
        val fixture = Fixture(audioPermission = true, shouldShow = false)

        fixture.owner.initialize()

        assertFalse(fixture.owner.showOnboarding())
        assertTrue(fixture.calls.isEmpty())
    }

    private class Fixture(
        private val audioPermission: Boolean,
        shouldShow: Boolean
    ) {
        val calls = mutableListOf<String>()
        val scheduler = FakeScheduler()
        val owner = OnboardingOwner(
            permissionAccess = OnboardingPermissionAccess(
                audioPermissionGranted = { audioPermission },
                notificationPermissionGranted = { false },
                permissionRequester = { calls += "request-permissions" }
            ),
            libraryAccess = OnboardingLibraryAccess(
                loader = { calls += "load:$it" },
                canceller = { calls += "cancel-load" }
            ),
            networkNavigator = { calls += "navigate:$it" },
            playlistPicker = { calls += "open-playlist" },
            audioFolderPicker = { calls += "open-folder" },
            completionStore = OnboardingCompletionStore(
                shouldShowSource = { shouldShow },
                completedWriter = { calls += "completed" }
            ),
            languageSource = { AppLanguage.MODE_ENGLISH },
            statusSink = { calls += "status:$it" },
            scheduler = scheduler
        )
    }

    private class FakeScheduler : OnboardingController.Scheduler {
        private var pending: Runnable? = null

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pending = runnable
        }

        override fun removeCallbacks(runnable: Runnable) {
            if (pending === runnable) pending = null
        }

        fun runPending() {
            pending.also { pending = null }?.run()
        }
    }
}
