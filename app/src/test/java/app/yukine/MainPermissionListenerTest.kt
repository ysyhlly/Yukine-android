package app.yukine

import app.yukine.di.PlatformModule
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPermissionListenerTest {
    @Test
    fun loadsLibraryAndMountsNavHostAfterGrantedPermissionWhenOnboardingVisible() {
        val calls = mutableListOf<String>()
        val listener = MainPermissionListener(
            audioPermissionStatusSource = AudioPermissionStatusSource { true },
            libraryLoader = PermissionResultLibraryLoader { calls += "load:$it" },
            onboardingVisibilitySource = OnboardingVisibilitySource { true },
            navHostMounter = PermissionResultNavHostMounter { calls += "mount" }
        )

        listener.onAudioPermissionResult()

        assertEquals(listOf("load:false", "mount"), calls)
    }

    @Test
    fun skipsLibraryLoadWhenAudioPermissionIsMissing() {
        val calls = mutableListOf<String>()
        val listener = MainPermissionListener(
            audioPermissionStatusSource = AudioPermissionStatusSource { false },
            libraryLoader = PermissionResultLibraryLoader { calls += "load:$it" },
            onboardingVisibilitySource = OnboardingVisibilitySource { false },
            navHostMounter = PermissionResultNavHostMounter { calls += "mount" }
        )

        listener.onAudioPermissionResult()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun factoryCreatesPermissionControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlatformModule.provideMainPermissionListenerFactory().create(
            AudioPermissionStatusSource { true },
            PermissionResultLibraryLoader { calls += "load:$it" },
            OnboardingVisibilitySource { false },
            PermissionResultNavHostMounter { calls += "mount" }
        )

        listener.onAudioPermissionResult()

        assertEquals(listOf("load:false"), calls)
    }
}
