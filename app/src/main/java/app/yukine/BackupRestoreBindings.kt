package app.yukine

internal fun interface BackupStatusAction {
    fun apply(statusKey: String)
}

internal class BackupRestoreBindings(
    private val statusAction: BackupStatusAction
) : BackupRestoreLauncher.Listener {
    override fun backupStatus(statusKey: String) {
        statusAction.apply(statusKey)
    }
}
