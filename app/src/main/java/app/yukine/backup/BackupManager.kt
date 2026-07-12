package app.yukine.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Xml
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

object BackupManager {
    private const val DATABASE_NAME = "echo_next.db"
    private val DB_FILES = listOf(DATABASE_NAME, "$DATABASE_NAME-wal", "$DATABASE_NAME-shm")
    private const val PREFS_DIR = "shared_prefs"
    private val PREFS_FILES = listOf("streaming_local_auth.xml")
    private const val RESTORE_ROOT = "backup-restore"
    private const val PENDING_DIR = "pending"
    private const val CANDIDATE_DIR = "candidate"
    private const val APPLY_MARKER = ".apply-state"
    private const val APPLY_PREPARED = "prepared"
    private const val APPLY_COMMITTED = "committed"
    private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L

    fun export(context: Context, outputUri: Uri): Boolean {
        return try {
            val output = context.contentResolver.openOutputStream(outputUri) ?: return false
            output.use { export(context, it) }
        } catch (_: Exception) {
            false
        }
    }

    internal fun export(context: Context, output: OutputStream): Boolean {
        return try {
            ZipOutputStream(output).use { zip -> writeBackup(context, zip) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates and stages a restore. Live database files are never touched here; the staged
     * restore is applied by [applyPendingRestore] during the next application startup.
     */
    fun stageRestore(context: Context, inputUri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(inputUri) ?: return false
            input.use { stageRestore(context, it) }
        } catch (_: Exception) {
            false
        }
    }

    internal fun stageRestore(context: Context, input: InputStream): Boolean {
        val root = restoreRoot(context)
        val candidate = File(root, CANDIDATE_DIR)
        val pending = File(root, PENDING_DIR)
        candidate.deleteRecursively()
        if (!candidate.mkdirs()) {
            return false
        }
        return try {
            ZipInputStream(input).use { zip -> extractValidatedEntries(zip, candidate) } &&
                validateStagedRestore(candidate) &&
                replacePending(candidate, pending)
        } catch (_: Exception) {
            false
        } finally {
            candidate.deleteRecursively()
        }
    }

    /** Applies a previously validated restore before repositories open the database. */
    fun applyPendingRestore(context: Context): Boolean {
        val pending = File(restoreRoot(context), PENDING_DIR)
        if (!pending.exists()) {
            return true
        }
        val targets = restoreTargets(context, pending)
        recoverInterruptedApply(pending, targets)
        if (!pending.exists()) {
            return true
        }
        if (!validateStagedRestore(pending)) {
            pending.deleteRecursively()
            return false
        }
        return applyWithRollback(pending, targets)
    }

    internal fun hasPendingRestore(context: Context): Boolean =
        File(restoreRoot(context), PENDING_DIR).exists()

    private fun writeBackup(context: Context, zip: ZipOutputStream) {
        val dbDir = context.getDatabasePath(DATABASE_NAME).parentFile
            ?: throw IllegalStateException("Database directory unavailable")
        if (!File(dbDir, DATABASE_NAME).isFile) {
            throw IllegalStateException("Database file unavailable")
        }
        for (name in DB_FILES) {
            writeFileIfPresent(zip, File(dbDir, name), "db/$name")
        }
        val prefsDir = File(context.applicationInfo.dataDir, PREFS_DIR)
        for (name in PREFS_FILES) {
            writeFileIfPresent(zip, File(prefsDir, name), "prefs/$name")
        }
    }

    private fun writeFileIfPresent(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) {
            return
        }
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun extractValidatedEntries(zip: ZipInputStream, candidate: File): Boolean {
        val allowedEntries = buildMap {
            DB_FILES.forEach { name -> put("db/$name", File(candidate, "db/$name")) }
            PREFS_FILES.forEach { name -> put("prefs/$name", File(candidate, "prefs/$name")) }
        }
        val seen = HashSet<String>()
        var totalBytes = 0L
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val target = allowedEntries[entry.name] ?: return false
                if (!seen.add(entry.name) || entry.size > MAX_ENTRY_BYTES) {
                    return false
                }
                target.parentFile?.mkdirs()
                val copied = copyLimited(zip, target, MAX_ENTRY_BYTES)
                totalBytes += copied
                if (totalBytes > MAX_TOTAL_BYTES) {
                    return false
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return "db/$DATABASE_NAME" in seen
    }

    private fun copyLimited(input: InputStream, target: File, limit: Long): Long {
        var copied = 0L
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                copied += count
                if (copied > limit) {
                    throw IllegalArgumentException("Backup entry is too large")
                }
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
        return copied
    }

    private fun validateStagedRestore(directory: File): Boolean {
        val databaseFile = File(directory, "db/$DATABASE_NAME")
        if (!databaseFile.isFile || !hasSqliteHeader(databaseFile)) {
            return false
        }
        val databaseValid = try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                database.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
        return databaseValid && validateStagedPreferences(directory)
    }

    private fun validateStagedPreferences(directory: File): Boolean {
        return PREFS_FILES.all { name ->
            val file = File(directory, "prefs/$name")
            !file.exists() || validatePreferencesXml(file)
        }
    }

    private fun validatePreferencesXml(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                parser.setInput(input, Charsets.UTF_8.name())
                var event = parser.eventType
                while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                    event = parser.next()
                }
                if (event != XmlPullParser.START_TAG || parser.name != "map") {
                    return false
                }
                while (event != XmlPullParser.END_DOCUMENT) {
                    event = parser.next()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val expected = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (file.length() < expected.size) {
            return false
        }
        val actual = ByteArray(expected.size)
        FileInputStream(file).use { input ->
            if (input.read(actual) != actual.size) {
                return false
            }
        }
        return actual.contentEquals(expected)
    }

    private fun replacePending(candidate: File, pending: File): Boolean {
        val root = pending.parentFile ?: return false
        if (!root.exists() && !root.mkdirs()) {
            return false
        }
        pending.deleteRecursively()
        return candidate.renameTo(pending)
    }

    private data class RestoreTarget(
        val staged: File,
        val target: File
    )

    private fun restoreTargets(context: Context, pending: File): List<RestoreTarget> {
        val dbDir = context.getDatabasePath(DATABASE_NAME).parentFile
            ?: throw IllegalStateException("Database directory unavailable")
        val prefsDir = File(context.applicationInfo.dataDir, PREFS_DIR)
        return buildList {
            DB_FILES.forEach { name ->
                add(RestoreTarget(File(pending, "db/$name"), File(dbDir, name)))
            }
            PREFS_FILES.forEach { name ->
                add(RestoreTarget(File(pending, "prefs/$name"), File(prefsDir, name)))
            }
        }
    }

    private fun applyWithRollback(pending: File, targets: List<RestoreTarget>): Boolean {
        val marker = File(pending, APPLY_MARKER)
        try {
            prepareReplacementFiles(targets)
            writeSynced(marker, APPLY_PREPARED)
        } catch (_: Exception) {
            cleanupReplacementFiles(targets)
            marker.delete()
            return false
        }
        return try {
            commitReplacementFiles(targets)
            writeSynced(marker, APPLY_COMMITTED)
            cleanupReplacementFiles(targets)
            pending.deleteRecursively()
            true
        } catch (_: Exception) {
            rollbackReplacementFiles(targets)
            marker.delete()
            false
        }
    }

    private fun prepareReplacementFiles(targets: List<RestoreTarget>) {
        targets.forEach { restore ->
            restore.target.parentFile?.mkdirs()
            oldFile(restore.target).delete()
            newFile(restore.target).delete()
            if (restore.target.exists()) {
                copyFileSynced(restore.target, oldFile(restore.target))
            }
            if (restore.staged.exists()) {
                copyFileSynced(restore.staged, newFile(restore.target))
            }
        }
    }

    private fun commitReplacementFiles(targets: List<RestoreTarget>) {
        targets.forEach { restore ->
            if (restore.target.exists() && !restore.target.delete()) {
                throw IllegalStateException("Could not replace ${restore.target.name}")
            }
            val replacement = newFile(restore.target)
            if (replacement.exists() && !replacement.renameTo(restore.target)) {
                throw IllegalStateException("Could not commit ${restore.target.name}")
            }
        }
    }

    private fun recoverInterruptedApply(pending: File, targets: List<RestoreTarget>) {
        val marker = File(pending, APPLY_MARKER)
        if (!marker.exists()) {
            cleanupReplacementFiles(targets)
            return
        }
        if (marker.readText(Charsets.UTF_8).trim() == APPLY_COMMITTED) {
            cleanupReplacementFiles(targets)
            pending.deleteRecursively()
            return
        }
        rollbackReplacementFiles(targets)
        marker.delete()
    }

    private fun rollbackReplacementFiles(targets: List<RestoreTarget>) {
        targets.forEach { restore ->
            restore.target.delete()
            val original = oldFile(restore.target)
            if (original.exists() && !original.renameTo(restore.target)) {
                copyFileSynced(original, restore.target)
                original.delete()
            }
            newFile(restore.target).delete()
        }
    }

    private fun cleanupReplacementFiles(targets: List<RestoreTarget>) {
        targets.forEach { restore ->
            oldFile(restore.target).delete()
            newFile(restore.target).delete()
        }
    }

    private fun copyFileSynced(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun writeSynced(file: File, text: String) {
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun oldFile(target: File): File = File(target.parentFile, ".yukine-restore-old-${target.name}")

    private fun newFile(target: File): File = File(target.parentFile, ".yukine-restore-new-${target.name}")

    private fun restoreRoot(context: Context): File = File(context.filesDir, RESTORE_ROOT)
}
