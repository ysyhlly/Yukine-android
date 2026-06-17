package app.yukine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object AppPermissions {
    @JvmStatic
    fun requestNeededPermissions(activity: Activity, requestCode: Int) {
        val permissions = ArrayList<String>()
        if (!hasAudioPermission(activity)) {
            permissions.add(audioPermission())
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(activity)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            activity.requestPermissions(permissions.toTypedArray(), requestCode)
        }
    }

    @JvmStatic
    fun hasAudioPermission(context: Context): Boolean =
        context.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return true
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
