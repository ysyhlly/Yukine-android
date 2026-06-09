package app.echo.next

import android.content.Context
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.ui.EchoDialog

internal class ConfirmationDialogController(
    private val context: Context,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    interface LanguageProvider {
        fun languageMode(): String
    }

    interface Listener {
        fun clearPlayHistory()

        fun clearQueue()

        fun deleteAllStreams()

        fun deleteTrack(trackId: Long, status: String)

        fun deleteRemoteSource(sourceId: Long, name: String)
    }

    fun confirmClearPlayHistory() {
        confirm(text("clear.play.history.title"), text("clear.play.history.message")) {
            listener.clearPlayHistory()
        }
    }

    fun confirmClearQueue() {
        confirm(text("clear.queue.title"), text("clear.queue.message")) {
            listener.clearQueue()
        }
    }

    fun confirmDeleteAllStreams() {
        confirm(text("delete.all.streams.title"), text("delete.all.streams.message")) {
            listener.deleteAllStreams()
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
            listener.deleteTrack(track.id, text("deleted.stream"))
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
            listener.deleteRemoteSource(source.id, source.name)
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
