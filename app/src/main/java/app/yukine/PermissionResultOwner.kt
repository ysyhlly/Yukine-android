package app.yukine

/** Owns the app consequences of a completed runtime-permission request. */
internal class PermissionResultOwner(
    private val audioPermissionStatusSource: AudioPermissionStatusSource,
    private val libraryLoader: LibraryLoader,
    private val permissionResultObserver: PermissionResultObserver
) : MainPermissionController.Listener {
    fun interface AudioPermissionStatusSource {
        fun hasAudioPermission(): Boolean
    }

    fun interface LibraryLoader {
        fun loadLibrary(allowCachedFirst: Boolean)
    }

    fun interface PermissionResultObserver {
        fun onPermissionsChanged()
    }

    override fun onAudioPermissionResult() {
        if (audioPermissionStatusSource.hasAudioPermission()) {
            libraryLoader.loadLibrary(false)
        }
        permissionResultObserver.onPermissionsChanged()
    }
}
