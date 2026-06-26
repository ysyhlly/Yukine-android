package app.yukine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal object AppPermissions {
    @JvmStatic
    fun neededPermissions(context: Context): Array<String> {
        val permissions = ArrayList<String>()
        if (!hasAudioPermission(context)) {
            permissions.add(audioPermission())
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
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

    @JvmStatic
    fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    @JvmStatic
    fun openOverlayPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        )
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
