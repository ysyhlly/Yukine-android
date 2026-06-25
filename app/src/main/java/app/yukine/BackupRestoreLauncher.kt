package app.yukine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import app.yukine.backup.BackupManager

internal class BackupRestoreLauncher(
    private val activity: ComponentActivity,
    private val listener: Listener
) {
    interface Listener {
        fun backupStatus(statusKey: String)
    }

    fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.type = "application/zip"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_TITLE, BACKUP_FILE_NAME)
        activity.startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
    }

    fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "application/zip"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        activity.startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_EXPORT_BACKUP && requestCode != REQUEST_IMPORT_BACKUP) {
            return false
        }
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            return true
        }
        val statusKey = when (requestCode) {
            REQUEST_EXPORT_BACKUP -> exportStatus(uri)
            REQUEST_IMPORT_BACKUP -> importStatus(uri)
            else -> return false
        }
        listener.backupStatus(statusKey)
        return true
    }

    private fun exportStatus(uri: Uri): String {
        val ok = BackupManager.export(activity, uri)
        return if (ok) "backup.export.success" else "backup.export.failed"
    }

    private fun importStatus(uri: Uri): String {
        val ok = BackupManager.restore(activity, uri)
        return if (ok) "backup.import.success" else "backup.import.failed"
    }

    companion object {
        @JvmField val REQUEST_EXPORT_BACKUP: Int = 9010
        @JvmField val REQUEST_IMPORT_BACKUP: Int = 9011
        private const val BACKUP_FILE_NAME = "yukine-backup.zip"
    }
}
