package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRestoreLauncherTest {
    @Test
    fun exportBackupLaunchesCreateZipDocument() {
        val launcher = RecordingBackupActivityResultLauncher()
        val owner = BackupRestoreLauncher(
            activity = activity(),
            statusSink = {},
            activityResultLauncher = launcher
        )

        owner.exportBackup()

        val intent = launcher.launches.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals("yukine-backup.zip", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun importBackupLaunchesOpenZipDocument() {
        val launcher = RecordingBackupActivityResultLauncher()
        val owner = BackupRestoreLauncher(
            activity = activity(),
            statusSink = {},
            activityResultLauncher = launcher
        )

        owner.importBackup()

        val intent = launcher.launches.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
    }

    @Test
    fun canceledOrMissingUriResultsDoNotEmitStatus() {
        val launcher = RecordingBackupActivityResultLauncher()
        val statuses = mutableListOf<String>()
        val owner = BackupRestoreLauncher(
            activity = activity(),
            statusSink = { statuses += it },
            activityResultLauncher = launcher
        )

        owner.exportBackup()
        launcher.emit(ActivityResult(Activity.RESULT_OK, Intent()))
        owner.importBackup()
        launcher.emit(ActivityResult(Activity.RESULT_CANCELED, null))

        assertEquals(emptyList<String>(), statuses)
    }

    @Test
    fun exportAndImportResultsEmitMappedStatusKeys() {
        val launcher = RecordingBackupActivityResultLauncher()
        val confirmer = RecordingBackupImportConfirmer()
        val statuses = mutableListOf<String>()
        val operations = RecordingBackupOperations(exportOk = true, restoreOk = false)
        val owner = BackupRestoreLauncher(
            activity = activity(),
            statusSink = { statuses += it },
            activityResultLauncher = launcher,
            operations = operations,
            importConfirmer = confirmer
        )
        val exportUri = Uri.parse("content://backup/export")
        val importUri = Uri.parse("content://backup/import")

        owner.exportBackup()
        launcher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(exportUri)))
        owner.importBackup()
        launcher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(importUri)))

        assertEquals(listOf("backup.export.success"), statuses)
        assertEquals(emptyList<Uri>(), operations.restored)

        confirmer.confirm()

        assertEquals(listOf("backup.export.success", "backup.import.failed"), statuses)
        assertEquals(listOf(exportUri), operations.exported)
        assertEquals(listOf(importUri), operations.restored)
    }

    private fun activity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private class RecordingBackupActivityResultLauncher : BackupActivityResultLauncher {
        val launches = mutableListOf<Intent>()
        private var callback: ((ActivityResult) -> Unit)? = null

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            launches += intent
            callback = onResult
        }

        fun emit(result: ActivityResult) {
            callback?.invoke(result)
        }
    }

    private class RecordingBackupOperations(
        private val exportOk: Boolean,
        private val restoreOk: Boolean
    ) : BackupFileOperations {
        val exported = mutableListOf<Uri>()
        val restored = mutableListOf<Uri>()

        override fun export(context: Context, uri: Uri): Boolean {
            exported += uri
            return exportOk
        }

        override fun stageRestore(context: Context, uri: Uri): Boolean {
            restored += uri
            return restoreOk
        }
    }

    private class RecordingBackupImportConfirmer : BackupImportConfirmer {
        private var pending: Runnable? = null

        override fun confirm(onConfirm: Runnable) {
            pending = onConfirm
        }

        fun confirm() {
            pending?.run()
            pending = null
        }
    }

}
