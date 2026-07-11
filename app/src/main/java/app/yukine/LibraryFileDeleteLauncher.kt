package app.yukine

import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.yukine.model.Track
import app.yukine.ui.EchoDialog
import app.yukine.ui.LibrarySourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun interface LibraryDeleteIntentLauncher {
    fun launch(request: IntentSenderRequest, onResult: (ActivityResult) -> Unit)
}

internal class LibraryFileDeleteLauncher @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val useCase: LibraryDeletionUseCase,
    private val languageMode: () -> String,
    private val onCompleted: (LibraryDeletionResult) -> Unit,
    intentLauncher: LibraryDeleteIntentLauncher? = null
) {
    private val resolver: ContentResolver = activity.contentResolver
    private val intentLauncher = intentLauncher ?: ActivityResultDeleteIntentLauncher(activity)

    fun request(tracks: List<Track>, playlistId: Long = -1L) {
        val unique = tracks.filter { it.id >= 0L }.distinctBy { it.id to it.dataPath }
        if (unique.isEmpty()) return
        if (playlistId >= 0L) {
            confirm(
                text("library.remove.playlist.title"),
                countMessage("library.remove.playlist.message", unique.size)
            ) { runMutation { useCase.removeFromPlaylist(playlistId, unique) } }
            return
        }
        val local = unique.filter { LibraryTrackPresentationPolicy.sourceKind(it) in LOCAL_SOURCES }
        if (local.isEmpty()) {
            confirm(
                text("library.delete.records.title"),
                countMessage("library.delete.records.message", unique.size)
            ) { runMutation { useCase.removeFromLibrary(unique) } }
            return
        }
        val message = countMessage("library.delete.choose.message", unique.size)
        EchoDialog.builder(activity)
            .setTitle(text("library.delete.choose.title"))
            .setMessage(message)
            .setItems(
                arrayOf(text("library.hide.action"), text("library.delete.file.action"))
            ) { _, which ->
                if (which == 0) {
                    runMutation { useCase.removeFromLibrary(unique) }
                } else {
                    deleteFiles(local, unique - local.toSet())
                }
            }
            .setNegativeButton(text("cancel"), null)
            .show()
    }

    private fun deleteFiles(local: List<Track>, skipped: List<Track>) {
        val media = local.filter {
            LibraryTrackPresentationPolicy.sourceKind(it) == LibrarySourceKind.MediaStore &&
                it.contentUri?.scheme == ContentResolver.SCHEME_CONTENT &&
                it.contentUri?.authority == MediaStore.AUTHORITY
        }
        val documents = local - media.toSet()
        activity.lifecycleScope.launch {
            val direct = withContext(Dispatchers.IO) { deleteDirectDocuments(documents) }
            if (media.isEmpty()) {
                finalizeDeleted(direct.first, direct.second, skipped)
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                launchModernMediaDelete(media, direct.first, direct.second, skipped)
            } else {
                deleteLegacyMedia(media, 0, direct.first.toMutableList(), direct.second.toMutableList(), skipped)
            }
        }
    }

    private fun launchModernMediaDelete(
        media: List<Track>,
        deleted: List<Track>,
        failed: List<Track>,
        skipped: List<Track>
    ) {
        val uris = media.mapNotNull { validContentUri(it) }
        if (uris.isEmpty()) {
            finalizeDeleted(deleted, failed + media, skipped)
            return
        }
        val pendingIntent = try {
            MediaStore.createDeleteRequest(resolver, uris)
        } catch (_: IllegalArgumentException) {
            finalizeDeleted(deleted, failed + media, skipped)
            return
        } catch (_: SecurityException) {
            finalizeDeleted(deleted, failed + media, skipped)
            return
        }
        intentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                activity.lifecycleScope.launch {
                    val (confirmed, remaining) = withContext(Dispatchers.IO) {
                        media.partition { !contentUriExists(it) }
                    }
                    finalizeDeleted(deleted + confirmed, failed + remaining, skipped)
                }
            } else {
                onCompleted(LibraryDeletionResult(deleted, failed, skipped + media, cancelled = true))
            }
        }
    }

    private fun deleteLegacyMedia(
        tracks: List<Track>,
        index: Int,
        deleted: MutableList<Track>,
        failed: MutableList<Track>,
        skipped: List<Track>
    ) {
        val track = tracks.getOrNull(index)
        if (track == null) {
            finalizeDeleted(deleted, failed, skipped)
            return
        }
        activity.lifecycleScope.launch {
            when (val outcome = withContext(Dispatchers.IO) { tryDeleteMedia(track) }) {
                DirectDeleteOutcome.Deleted -> deleted.add(track)
                DirectDeleteOutcome.Failed -> failed.add(track)
                is DirectDeleteOutcome.NeedsPermission -> {
                    intentLauncher.launch(
                        IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            deleteLegacyMedia(tracks, index, deleted, failed, skipped)
                        } else {
                            failed.add(track)
                            deleteLegacyMedia(tracks, index + 1, deleted, failed, skipped)
                        }
                    }
                    return@launch
                }
            }
            deleteLegacyMedia(tracks, index + 1, deleted, failed, skipped)
        }
    }

    private fun tryDeleteMedia(track: Track): DirectDeleteOutcome {
        val uri = validContentUri(track) ?: return deleteFilePath(track)
        return try {
            if (resolver.delete(uri, null, null) > 0) DirectDeleteOutcome.Deleted else DirectDeleteOutcome.Failed
        } catch (error: RecoverableSecurityException) {
            DirectDeleteOutcome.NeedsPermission(error.userAction.actionIntent)
        } catch (_: SecurityException) {
            DirectDeleteOutcome.Failed
        } catch (_: IllegalArgumentException) {
            DirectDeleteOutcome.Failed
        }
    }

    private fun deleteDirectDocuments(tracks: List<Track>): Pair<List<Track>, List<Track>> {
        val deleted = ArrayList<Track>()
        val failed = ArrayList<Track>()
        tracks.forEach { track ->
            val uri = validContentUri(track)
            val ok = try {
                when {
                    uri != null && DocumentsContract.isDocumentUri(activity, uri) ->
                        DocumentsContract.deleteDocument(resolver, uri)
                    uri?.scheme == ContentResolver.SCHEME_FILE -> File(uri.path.orEmpty()).delete()
                    else -> deleteFilePath(track) == DirectDeleteOutcome.Deleted
                }
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
            if (ok) deleted.add(track) else failed.add(track)
        }
        return deleted to failed
    }

    private fun deleteFilePath(track: Track): DirectDeleteOutcome {
        val path = track.dataPath.removePrefix("document:")
        return if (path.isNotBlank() && File(path).delete()) DirectDeleteOutcome.Deleted else DirectDeleteOutcome.Failed
    }

    private fun finalizeDeleted(deleted: List<Track>, failed: List<Track>, skipped: List<Track>) {
        activity.lifecycleScope.launch {
            val finalized = withContext(Dispatchers.IO) { useCase.finalizeDeletedFiles(deleted) }
            onCompleted(
                LibraryDeletionResult(
                    removed = finalized.removed,
                    failed = failed + finalized.failed,
                    skipped = skipped
                )
            )
        }
    }

    private fun runMutation(block: () -> LibraryDeletionResult) {
        activity.lifecycleScope.launch {
            onCompleted(withContext(Dispatchers.IO) { block() })
        }
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        EchoDialog.builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(text("cancel"), null)
            .setPositiveButton(text("ok")) { _, _ -> action() }
            .show()
    }

    private fun countMessage(key: String, count: Int): String = text(key).replace("%d", count.toString())

    private fun text(key: String): String = AppLanguage.text(languageMode(), key)

    private fun validContentUri(track: Track): Uri? = track.contentUri?.takeIf { it != Uri.EMPTY && it.scheme != null }

    private fun contentUriExists(track: Track): Boolean {
        val uri = validContentUri(track) ?: return true
        return try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private sealed interface DirectDeleteOutcome {
        data object Deleted : DirectDeleteOutcome
        data object Failed : DirectDeleteOutcome
        data class NeedsPermission(val pendingIntent: PendingIntent) : DirectDeleteOutcome
    }

    companion object {
        private val LOCAL_SOURCES = setOf(LibrarySourceKind.MediaStore, LibrarySourceKind.Document)

        private class ActivityResultDeleteIntentLauncher(activity: ComponentActivity) : LibraryDeleteIntentLauncher {
            private var callback: ((ActivityResult) -> Unit)? = null
            private val launcher = activity.registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result -> callback?.invoke(result) }

            override fun launch(request: IntentSenderRequest, onResult: (ActivityResult) -> Unit) {
                callback = onResult
                try {
                    launcher.launch(request)
                } catch (_: IntentSender.SendIntentException) {
                    callback?.invoke(ActivityResult(Activity.RESULT_CANCELED, null))
                }
            }
        }
    }
}
