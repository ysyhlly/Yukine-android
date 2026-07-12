package app.yukine.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath("echo_next.db")
        context.deleteDatabase("echo_next.db")
        File(context.filesDir, "backup-restore").deleteRecursively()
        cleanupRestoreTemps()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("echo_next.db")
        File(context.filesDir, "backup-restore").deleteRecursively()
        cleanupRestoreTemps()
    }

    @Test
    fun stageRestoreValidatesWithoutTouchingTheLiveDatabaseThenAppliesOnStartup() {
        writeDatabase(databaseFile, "old")
        val backupDatabase = File(context.cacheDir, "backup-source.db")
        backupDatabase.delete()
        writeDatabase(backupDatabase, "new")

        val staged = BackupManager.stageRestore(
            context,
            ByteArrayInputStream(backupZip(backupDatabase, includeUnknownEntry = false))
        )

        assertTrue(staged)
        assertTrue(BackupManager.hasPendingRestore(context))
        assertEquals("old", readDatabase(databaseFile))

        assertTrue(BackupManager.applyPendingRestore(context))
        assertFalse(BackupManager.hasPendingRestore(context))
        assertEquals("new", readDatabase(databaseFile))
    }

    @Test
    fun malformedOrUnexpectedBackupIsRejectedWithoutReplacingPendingRestore() {
        val malformed = zipOf("db/echo_next.db" to "not sqlite".toByteArray())
        assertFalse(BackupManager.stageRestore(context, ByteArrayInputStream(malformed)))
        assertFalse(BackupManager.hasPendingRestore(context))

        val validDatabase = File(context.cacheDir, "backup-bad-prefs.db")
        validDatabase.delete()
        writeDatabase(validDatabase, "new")
        val badPreferences = zipOf(
            "db/echo_next.db" to validDatabase.readBytes(),
            "prefs/streaming_local_auth.xml" to "<not-map>".toByteArray()
        )
        assertFalse(BackupManager.stageRestore(context, ByteArrayInputStream(badPreferences)))
        assertFalse(BackupManager.hasPendingRestore(context))

        val unknownEntryDatabase = File(context.cacheDir, "backup-unknown.db")
        unknownEntryDatabase.delete()
        writeDatabase(unknownEntryDatabase, "new")
        assertFalse(
            BackupManager.stageRestore(
                context,
                ByteArrayInputStream(backupZip(unknownEntryDatabase, includeUnknownEntry = true))
            )
        )
        assertFalse(BackupManager.hasPendingRestore(context))
    }

    @Test
    fun failedPreparationKeepsAllOriginalFilesAndPendingRestoreForRetry() {
        writeDatabase(databaseFile, "old")
        val currentPrefs = File(context.applicationInfo.dataDir, "shared_prefs/streaming_local_auth.xml")
        currentPrefs.parentFile?.mkdirs()
        currentPrefs.writeText("<map><string name=\"token\">old</string></map>")
        val backupDatabase = File(context.cacheDir, "backup-rollback.db")
        backupDatabase.delete()
        writeDatabase(backupDatabase, "new")
        assertTrue(
            BackupManager.stageRestore(
                context,
                ByteArrayInputStream(backupZip(backupDatabase, includeUnknownEntry = false))
            )
        )
        val blockedReplacement = File(databaseFile.parentFile, ".yukine-restore-new-${databaseFile.name}")
        blockedReplacement.mkdirs()
        File(blockedReplacement, "block").writeText("block")

        assertFalse(BackupManager.applyPendingRestore(context))

        assertEquals("old", readDatabase(databaseFile))
        assertEquals("<map><string name=\"token\">old</string></map>", currentPrefs.readText())
        assertTrue(BackupManager.hasPendingRestore(context))
        blockedReplacement.deleteRecursively()
    }

    private fun backupZip(database: File, includeUnknownEntry: Boolean): ByteArray {
        val entries = linkedMapOf("db/echo_next.db" to database.readBytes())
        if (includeUnknownEntry) {
            entries["../unexpected"] = byteArrayOf(1)
        }
        return zipOf(*entries.map { it.key to it.value }.toTypedArray())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun writeDatabase(file: File, value: String) {
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS marker(value TEXT NOT NULL)")
            database.execSQL("DELETE FROM marker")
            database.execSQL("INSERT INTO marker(value) VALUES(?)", arrayOf(value))
        } finally {
            database.close()
        }
    }

    private fun readDatabase(file: File): String {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("SELECT value FROM marker", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        } finally {
            database.close()
        }
    }

    private fun cleanupRestoreTemps() {
        databaseFile.parentFile?.listFiles()?.forEach { file ->
            if (file.name.startsWith(".yukine-restore-")) {
                file.deleteRecursively()
            }
        }
    }
}
