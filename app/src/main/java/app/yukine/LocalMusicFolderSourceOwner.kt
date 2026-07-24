package app.yukine

import android.net.Uri
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.LocalMusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.function.Consumer

/** Owns configured music-folder state and all non-picker source mutations. */
internal class LocalMusicFolderSourceOwner(
    private val repository: MusicLibraryRepository,
    private val documentPickerController: DocumentPickerController,
    private val runIo: Consumer<Runnable>,
    private val postMain: Consumer<Runnable>,
    private val libraryReload: Runnable,
    private val settingsRefresh: Runnable,
    private val statusSink: Consumer<String>
) {
    private val mutableSources = MutableStateFlow<List<LocalMusicSource>>(emptyList())
    val state: StateFlow<List<LocalMusicSource>> = mutableSources.asStateFlow()

    fun initialize() = reloadSources()

    fun sourcesSnapshot(): List<LocalMusicSource> = state.value

    fun addFolder() = documentPickerController.openAudioFolderPicker()

    fun reauthorize(@Suppress("UNUSED_PARAMETER") sourceId: String) {
        documentPickerController.openAudioFolderPicker()
    }

    fun refreshAll() {
        runIo.accept(Runnable {
            val total = repository.loadLocalMusicFolderSources().size
            val refreshed = repository.refreshAllLocalMusicFolders()
            finishMutation(
                if (refreshed == total) "music.folder.refresh.completed"
                else "music.folder.refresh.partial"
            )
        })
    }

    fun refresh(sourceId: String) {
        runIo.accept(Runnable {
            val result = repository.refreshLocalMusicFolder(sourceId)
            finishMutation(
                if (result.complete()) "music.folder.refresh.completed"
                else "music.folder.refresh.failed"
            )
        })
    }

    fun remove(sourceId: String) {
        runIo.accept(Runnable {
            val rootUri = repository.removeLocalMusicFolder(sourceId)
            postMain.accept(Runnable {
                if (rootUri.isNotBlank()) {
                    documentPickerController.releaseAudioFolderPermission(Uri.parse(rootUri))
                }
                libraryReload.run()
                statusSink.accept("music.folder.remove.completed")
                reloadSources()
            })
        })
    }

    fun reloadSources() {
        runIo.accept(Runnable {
            val next = repository.loadLocalMusicFolderSources()
            postMain.accept(Runnable {
                mutableSources.value = next
                settingsRefresh.run()
            })
        })
    }

    private fun finishMutation(statusKey: String) {
        val next = repository.loadLocalMusicFolderSources()
        postMain.accept(Runnable {
            mutableSources.value = next
            libraryReload.run()
            statusSink.accept(statusKey)
            settingsRefresh.run()
        })
    }
}
