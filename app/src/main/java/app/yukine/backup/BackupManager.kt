package app.yukine.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private val DB_FILES = listOf("echo_next.db", "echo_next.db-wal", "echo_next.db-shm")
    private const val PREFS_DIR = "shared_prefs"
    private val PREFS_FILES = listOf("streaming_local_auth.xml")

    fun export(context: Context, outputUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                ZipOutputStream(out).use { zip -> writeBackup(context, zip) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restore(context: Context, inputUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                ZipInputStream(input).use { zip -> readBackup(context, zip) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeBackup(context: Context, zip: ZipOutputStream) {
        val dbDir = context.getDatabasePath("echo_next.db").parentFile ?: return
        for (name in DB_FILES) {
            val file = File(dbDir, name)
            if (file.exists()) {
                zip.putNextEntry(ZipEntry("db/$name"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val prefsDir = File(context.applicationInfo.dataDir, PREFS_DIR)
        for (name in PREFS_FILES) {
            val file = File(prefsDir, name)
            if (file.exists()) {
                zip.putNextEntry(ZipEntry("prefs/$name"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun readBackup(context: Context, zip: ZipInputStream) {
        val dbDir = context.getDatabasePath("echo_next.db").parentFile ?: return
        val prefsDir = File(context.applicationInfo.dataDir, PREFS_DIR)
        var entry = zip.nextEntry
        while (entry != null) {
            when {
                entry.name.startsWith("db/") -> {
                    val name = entry.name.removePrefix("db/")
                    if (name in DB_FILES) {
                        File(dbDir, name).outputStream().use { zip.copyTo(it) }
                    }
                }
                entry.name.startsWith("prefs/") -> {
                    val name = entry.name.removePrefix("prefs/")
                    if (name in PREFS_FILES) {
                        File(prefsDir, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}
