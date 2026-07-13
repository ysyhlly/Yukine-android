package app.yukine

import android.content.Context
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.ui.EchoDialog

internal fun interface PlayHistoryClearAction {
    fun clear()
}

internal fun interface QueueClearAction {
    fun clear()
}

internal fun interface AllStreamsDeleteAction {
    fun deleteAll()
}

internal fun interface StreamTrackDeleteAction {
    fun delete(trackId: Long, status: String)
}

internal fun interface StreamTracksDeleteAction {
    fun delete(trackIds: List<Long>, status: String)
}

internal fun interface RemoteSourceDeleteAction {
    fun delete(sourceId: Long)
}

internal data class ConfirmationActions(
    val clearPlayHistory: PlayHistoryClearAction,
    val clearQueue: QueueClearAction,
    val deleteAllStreams: AllStreamsDeleteAction,
    val deleteTrack: StreamTrackDeleteAction,
    val deleteTracks: StreamTracksDeleteAction,
    val deleteRemoteSource: RemoteSourceDeleteAction
)

internal class ConfirmationDialogController(
    private val context: Context,
    private val languageProvider: DialogLanguageProvider,
    private val actions: ConfirmationActions
) {
    fun confirmClearPlayHistory() {
        confirm(text("clear.play.history.title"), text("clear.play.history.message")) {
            actions.clearPlayHistory.clear()
        }
    }

    fun confirmClearQueue() {
        confirm(text("clear.queue.title"), text("clear.queue.message")) {
            actions.clearQueue.clear()
        }
    }

    fun confirmDeleteAllStreams() {
        confirm(text("delete.all.streams.title"), text("delete.all.streams.message")) {
            actions.deleteAllStreams.deleteAll()
        }
    }

    fun confirmDeleteTrack(track: Track?) {
        if (track == null) {
            return
        }
        confirm(
            text("delete.stream.title"),
            text("delete.stream.message.prefix") + track.title + text("delete.message.suffix")
        ) {
            actions.deleteTrack.delete(track.id, text("deleted.stream"))
        }
    }

    fun confirmDeleteTracks(title: String, tracks: List<Track>) {
        val ids = tracks.map { it.id }.filter(TrackIdentity::isUsable).distinct()
        if (ids.isEmpty()) {
            return
        }
        val name = title.ifBlank { text("songs") }
        confirm(
            text("delete"),
            text("delete.group.message.prefix") + name + text("delete.group.message.middle") + ids.size + text("delete.group.message.suffix")
        ) {
            actions.deleteTracks.delete(ids, text("deleted.group.prefix") + ids.size)
        }
    }

    fun confirmDeleteRemoteSource(source: RemoteSource?) {
        if (source == null) {
            return
        }
        confirm(
            text("delete.source.title"),
            text("delete.source.message.prefix") + source.name + text("delete.message.suffix")
        ) {
            actions.deleteRemoteSource.delete(source.id)
        }
    }

    private fun confirm(title: String, message: String, onConfirm: Runnable) {
        EchoDialog.builder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(text("cancel"), null)
            .setPositiveButton(text("ok")) { _, _ -> onConfirm.run() }
            .show()
    }

    private fun text(key: String): String = AppLanguage.text(languageProvider.languageMode(), key)
}
