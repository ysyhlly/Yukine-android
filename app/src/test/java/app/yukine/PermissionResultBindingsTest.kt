package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionResultBindingsTest {
    @Test
    fun loadsLibraryOnlyWhenAudioPermissionIsGranted() {
        val calls = mutableListOf<String>()
        var granted = false
        val listener = PermissionResultBindings(
            audioPermissionStateProvider = AudioPermissionStateProvider { granted },
            loadLibraryAction = Runnable { calls += "load" }
        )

        listener.onAudioPermissionResult()
        granted = true
        listener.onAudioPermissionResult()

        assertEquals(listOf("load"), calls)
    }
}
