package app.yukine

internal fun interface AudioPermissionStatusSource {
    fun hasAudioPermission(): Boolean
}

internal fun interface PermissionResultLibraryLoader {
    fun loadLibrary(allowCachedFirst: Boolean)
}

internal fun interface PermissionResultObserver {
    fun onPermissionsChanged()
}

internal fun interface MainPermissionListenerFactory {
    fun create(
        audioPermissionStatusSource: AudioPermissionStatusSource,
        libraryLoader: PermissionResultLibraryLoader,
        permissionResultObserver: PermissionResultObserver
    ): MainPermissionController.Listener
}

internal class MainPermissionListener(
    private val audioPermissionStatusSource: AudioPermissionStatusSource,
    private val libraryLoader: PermissionResultLibraryLoader,
    private val permissionResultObserver: PermissionResultObserver
) : MainPermissionController.Listener {
    override fun onAudioPermissionResult() {
        if (audioPermissionStatusSource.hasAudioPermission()) {
            libraryLoader.loadLibrary(false)
        }
        permissionResultObserver.onPermissionsChanged()
    }
}
