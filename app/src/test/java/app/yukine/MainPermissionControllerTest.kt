package app.yukine

import androidx.activity.ComponentActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainPermissionControllerTest {
    @Test
    fun requestNeededPermissionsLaunchesMissingPermissionsAndNotifiesOnResult() {
        val launcher = RecordingPermissionRequestLauncher()
        val listener = RecordingPermissionListener()
        val controller = MainPermissionController(
            activity = activity(),
            listener = listener,
            permissionRequestLauncher = launcher,
            neededPermissionsProvider = NeededPermissionsProvider {
                arrayOf("android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            }
        )

        controller.requestNeededPermissions()

        assertArrayEquals(
            arrayOf("android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS"),
            launcher.launches.single()
        )

        launcher.emit(mapOf("android.permission.READ_MEDIA_AUDIO" to true))

        assertEquals(1, listener.results)
    }

    @Test
    fun requestNeededPermissionsDoesNothingWhenNoPermissionsAreMissing() {
        val launcher = RecordingPermissionRequestLauncher()
        val listener = RecordingPermissionListener()
        val controller = MainPermissionController(
            activity = activity(),
            listener = listener,
            permissionRequestLauncher = launcher,
            neededPermissionsProvider = NeededPermissionsProvider { emptyArray() }
        )

        controller.requestNeededPermissions()

        assertTrue(launcher.launches.isEmpty())
        assertEquals(0, listener.results)
    }

    @Test
    fun requestAudioPermissionNeverIncludesNotificationPermission() {
        val launcher = RecordingPermissionRequestLauncher()
        val controller = MainPermissionController(
            activity = activity(),
            listener = RecordingPermissionListener(),
            permissionRequestLauncher = launcher
        )

        controller.requestAudioPermission()

        assertArrayEquals(
            arrayOf("android.permission.READ_MEDIA_AUDIO"),
            launcher.launches.single()
        )
    }

    private fun activity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private class RecordingPermissionRequestLauncher : PermissionRequestLauncher {
        val launches = mutableListOf<Array<String>>()
        private var callback: ((Map<String, Boolean>) -> Unit)? = null

        override fun launch(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit) {
            launches += permissions
            callback = onResult
        }

        fun emit(result: Map<String, Boolean>) {
            callback?.invoke(result)
        }
    }

    private class RecordingPermissionListener : MainPermissionController.Listener {
        var results = 0

        override fun onAudioPermissionResult() {
            results += 1
        }
    }
}
