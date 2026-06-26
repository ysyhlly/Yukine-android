package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.yukine.backup.BackupManager

internal fun interface BackupActivityResultLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal interface BackupFileOperations {
    fun export(context: Context, uri: Uri): Boolean
    fun restore(context: Context, uri: Uri): Boolean
}

internal fun interface BackupStatusSink {
    fun setStatusKey(statusKey: String)
}

internal class BackupRestoreLauncher @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val statusSink: BackupStatusSink,
    activityResultLauncher: BackupActivityResultLauncher? = null,
    private val operations: BackupFileOperations = BackupManagerOperations
) {
    private val activityResultLauncher =
        activityResultLauncher ?: ActivityResultBackupLauncher(activity)

    fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.type = "application/zip"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_TITLE, BACKUP_FILE_NAME)
        activityResultLauncher.launch(intent) { result ->
            handleBackupResult(result, BackupAction.EXPORT)
        }
    }

    fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "application/zip"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        activityResultLauncher.launch(intent) { result ->
            handleBackupResult(result, BackupAction.IMPORT)
        }
    }

    private fun handleBackupResult(result: ActivityResult, action: BackupAction) {
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            return
        }
        val statusKey = when (action) {
            BackupAction.EXPORT -> exportStatus(uri)
            BackupAction.IMPORT -> importStatus(uri)
        }
        statusSink.setStatusKey(statusKey)
    }

    private fun exportStatus(uri: Uri): String {
        val ok = operations.export(activity, uri)
        return if (ok) "backup.export.success" else "backup.export.failed"
    }

    private fun importStatus(uri: Uri): String {
        val ok = operations.restore(activity, uri)
        return if (ok) "backup.import.success" else "backup.import.failed"
    }

    private enum class BackupAction {
        EXPORT,
        IMPORT
    }

    companion object {
        private const val BACKUP_FILE_NAME = "yukine-backup.zip"

        private object BackupManagerOperations : BackupFileOperations {
            override fun export(context: Context, uri: Uri): Boolean =
                BackupManager.export(context, uri)

            override fun restore(context: Context, uri: Uri): Boolean =
                BackupManager.restore(context, uri)
        }

        private class ActivityResultBackupLauncher(
            activity: ComponentActivity
        ) : BackupActivityResultLauncher {
            private var callback: ((ActivityResult) -> Unit)? = null
            private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                callback?.invoke(result)
            }

            override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
                callback = onResult
                launcher.launch(intent)
            }
        }
    }
}
