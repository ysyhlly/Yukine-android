package app.yukine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRestoreLauncherTest {
    @Test
    fun exportBackupStartsCreateZipDocument() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = BackupRestoreLauncher(activity, RecordingListener())

        launcher.exportBackup()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(BackupRestoreLauncher.REQUEST_EXPORT_BACKUP, started.requestCode)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("application/zip", started.intent.type)
        assertTrue(started.intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals("yukine-backup.zip", started.intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun importBackupStartsOpenZipDocument() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = BackupRestoreLauncher(activity, RecordingListener())

        launcher.importBackup()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(BackupRestoreLauncher.REQUEST_IMPORT_BACKUP, started.requestCode)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertEquals("application/zip", started.intent.type)
        assertTrue(started.intent.categories.contains(Intent.CATEGORY_OPENABLE))
    }

    @Test
    fun handleActivityResultConsumesBackupRequestsWithoutUri() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val listener = RecordingListener()
        val launcher = BackupRestoreLauncher(activity, listener)

        assertTrue(launcher.handleActivityResult(BackupRestoreLauncher.REQUEST_EXPORT_BACKUP, Activity.RESULT_OK, Intent()))
        assertTrue(launcher.handleActivityResult(BackupRestoreLauncher.REQUEST_IMPORT_BACKUP, Activity.RESULT_CANCELED, null))
        assertEquals(emptyList<String>(), listener.statuses)
    }

    @Test
    fun handleActivityResultIgnoresUnrelatedRequests() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val listener = RecordingListener()
        val launcher = BackupRestoreLauncher(activity, listener)

        assertFalse(launcher.handleActivityResult(123, Activity.RESULT_OK, Intent().setData(Uri.parse("content://backup"))))
        assertEquals(emptyList<String>(), listener.statuses)
    }

    private class RecordingListener : BackupRestoreLauncher.Listener {
        val statuses = mutableListOf<String>()

        override fun backupStatus(statusKey: String) {
            statuses += statusKey
        }
    }
}
