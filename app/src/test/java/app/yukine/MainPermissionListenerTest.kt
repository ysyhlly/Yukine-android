package app.yukine

import app.yukine.di.PlatformModule
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPermissionListenerTest {
    @Test
    fun loadsLibraryAndPublishesPermissionResultAfterGrant() {
        val calls = mutableListOf<String>()
        val listener = MainPermissionListener(
            audioPermissionStatusSource = AudioPermissionStatusSource { true },
            libraryLoader = PermissionResultLibraryLoader { calls += "load:$it" },
            permissionResultObserver = PermissionResultObserver { calls += "permissions" }
        )

        listener.onAudioPermissionResult()

        assertEquals(listOf("load:false", "permissions"), calls)
    }

    @Test
    fun skipsLibraryLoadWhenAudioPermissionIsMissing() {
        val calls = mutableListOf<String>()
        val listener = MainPermissionListener(
            audioPermissionStatusSource = AudioPermissionStatusSource { false },
            libraryLoader = PermissionResultLibraryLoader { calls += "load:$it" },
            permissionResultObserver = PermissionResultObserver { calls += "permissions" }
        )

        listener.onAudioPermissionResult()

        assertEquals(listOf("permissions"), calls)
    }

    @Test
    fun factoryCreatesPermissionControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlatformModule.provideMainPermissionListenerFactory().create(
            AudioPermissionStatusSource { true },
            PermissionResultLibraryLoader { calls += "load:$it" },
            PermissionResultObserver { calls += "permissions" }
        )

        listener.onAudioPermissionResult()

        assertEquals(listOf("load:false", "permissions"), calls)
    }
}
