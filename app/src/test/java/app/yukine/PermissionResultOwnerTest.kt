package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionResultOwnerTest {
    @Test
    fun loadsLibraryAndPublishesPermissionResultAfterGrant() {
        val calls = mutableListOf<String>()
        val owner = PermissionResultOwner(
            audioPermissionStatusSource = { true },
            libraryLoader = { calls += "load:$it" },
            permissionResultObserver = { calls += "permissions" }
        )

        owner.onAudioPermissionResult()

        assertEquals(listOf("load:false", "permissions"), calls)
    }

    @Test
    fun skipsLibraryLoadButStillPublishesPermissionResultAfterDenial() {
        val calls = mutableListOf<String>()
        val owner = PermissionResultOwner(
            audioPermissionStatusSource = { false },
            libraryLoader = { calls += "load:$it" },
            permissionResultObserver = { calls += "permissions" }
        )

        owner.onAudioPermissionResult()

        assertEquals(listOf("permissions"), calls)
    }
}
