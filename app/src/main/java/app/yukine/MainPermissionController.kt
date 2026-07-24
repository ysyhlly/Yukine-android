package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

internal fun interface PermissionRequestLauncher {
    fun launch(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit)
}

internal fun interface NeededPermissionsProvider {
    fun neededPermissions(): Array<String>
}

internal class MainPermissionController @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val listener: Listener,
    permissionRequestLauncher: PermissionRequestLauncher? = null,
    neededPermissionsProvider: NeededPermissionsProvider? = null
) {
    private val permissionRequestLauncher: PermissionRequestLauncher =
        permissionRequestLauncher ?: ActivityResultPermissionRequestLauncher(activity)
    private val neededPermissionsProvider: NeededPermissionsProvider =
        neededPermissionsProvider ?: NeededPermissionsProvider { AppPermissions.neededPermissions(activity) }

    interface Listener {
        fun onAudioPermissionResult()
    }

    fun hasAudioPermission(): Boolean = AppPermissions.hasAudioPermission(activity)

    fun hasNotificationPermission(): Boolean = AppPermissions.hasNotificationPermission(activity)

    fun hasOverlayPermission(): Boolean = AppPermissions.hasOverlayPermission(activity)

    fun openOverlayPermissionSettings() {
        AppPermissions.openOverlayPermissionSettings(activity)
    }

    fun requestNeededPermissions() {
        requestPermissions(neededPermissionsProvider.neededPermissions())
    }

    fun requestAudioPermission() {
        requestPermissions(AppPermissions.neededAudioPermissions(activity))
    }

    private fun requestPermissions(permissions: Array<String>) {
        if (permissions.isEmpty()) {
            return
        }
        permissionRequestLauncher.launch(permissions) {
            listener.onAudioPermissionResult()
        }
    }

    private class ActivityResultPermissionRequestLauncher(
        activity: ComponentActivity
    ) : PermissionRequestLauncher {
        private var callback: ((Map<String, Boolean>) -> Unit)? = null
        private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            callback?.invoke(result)
        }

        override fun launch(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit) {
            callback = onResult
            launcher.launch(permissions)
        }
    }
}
