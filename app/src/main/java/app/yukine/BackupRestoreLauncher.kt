package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.yukine.backup.BackupManager
import app.yukine.ui.EchoDialog

internal fun interface BackupActivityResultLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal interface BackupFileOperations {
    fun export(context: Context, uri: Uri): Boolean
    fun stageRestore(context: Context, uri: Uri): Boolean
}

internal fun interface BackupImportConfirmer {
    fun confirm(onConfirm: Runnable)
}

internal class BackupRestoreLauncher @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val statusSink: (String) -> Unit,
    private val languageModeProvider: () -> String = { AppLanguage.MODE_SYSTEM },
    activityResultLauncher: BackupActivityResultLauncher? = null,
    private val operations: BackupFileOperations = BackupManagerOperations,
    importConfirmer: BackupImportConfirmer? = null
) {
    private val activityResultLauncher =
        activityResultLauncher ?: ActivityResultBackupLauncher(activity)
    private val importConfirmer = importConfirmer ?: DialogBackupImportConfirmer(
        activity,
        languageModeProvider
    )

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
            BackupAction.IMPORT -> {
                importConfirmer.confirm(Runnable { statusSink(importStatus(uri)) })
                return
            }
        }
        statusSink(statusKey)
    }

    private fun exportStatus(uri: Uri): String {
        val ok = operations.export(activity, uri)
        return if (ok) "backup.export.success" else "backup.export.failed"
    }

    private fun importStatus(uri: Uri): String {
        val ok = operations.stageRestore(activity, uri)
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

            override fun stageRestore(context: Context, uri: Uri): Boolean =
                BackupManager.stageRestore(context, uri)
        }

        private class DialogBackupImportConfirmer(
            private val activity: ComponentActivity,
            private val languageModeProvider: () -> String
        ) : BackupImportConfirmer {
            override fun confirm(onConfirm: Runnable) {
                EchoDialog.builder(activity)
                    .setTitle(text("backup.import.confirm.title"))
                    .setMessage(text("backup.import.confirm.message"))
                    .setNegativeButton(text("cancel"), null)
                    .setPositiveButton(text("backup.import.confirm.action")) { _, _ ->
                        onConfirm.run()
                    }
                    .show()
            }

            private fun text(key: String): String =
                AppLanguage.text(languageModeProvider(), key)
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
