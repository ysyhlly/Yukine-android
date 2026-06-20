package app.yukine

import androidx.activity.ComponentActivity

internal class MainPermissionController(
    private val activity: ComponentActivity,
    private val listener: Listener
) {
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
        AppPermissions.requestNeededPermissions(activity, REQUEST_AUDIO_PERMISSIONS)
    }

    fun handlePermissionsResult(requestCode: Int): Boolean {
        if (requestCode != REQUEST_AUDIO_PERMISSIONS) {
            return false
        }
        listener.onAudioPermissionResult()
        return true
    }

    private companion object {
        const val REQUEST_AUDIO_PERMISSIONS: Int = 4001
    }
}
