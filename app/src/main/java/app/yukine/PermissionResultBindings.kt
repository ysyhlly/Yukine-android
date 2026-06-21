package app.yukine

internal fun interface AudioPermissionStateProvider {
    fun hasAudioPermission(): Boolean
}

internal class PermissionResultBindings(
    private val audioPermissionStateProvider: AudioPermissionStateProvider,
    private val loadLibraryAction: Runnable
) : MainPermissionController.Listener {
    override fun onAudioPermissionResult() {
        if (audioPermissionStateProvider.hasAudioPermission()) {
            loadLibraryAction.run()
        }
    }
}
