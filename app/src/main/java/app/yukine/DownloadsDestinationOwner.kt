package app.yukine

/** Adapts download destination intents to its focused ViewModel and runtime controller. */
internal class DownloadsDestinationOwner(
    private val viewModel: DownloadsViewModel,
    private val downloadController: TrackDownloadDirectoryController,
    private val openDirectoryPicker: () -> Unit
) {
    fun actions(): DownloadsDestinationActions = DownloadsDestinationActions(
        refresh = { viewModel.refreshDirectory(downloadController) },
        useMusicDirectory = { viewModel.useMusicDirectory(downloadController) },
        useDownloadsDirectory = { viewModel.useDownloadsDirectory(downloadController) },
        chooseDirectory = { viewModel.chooseDirectory(downloadController) },
        pauseItem = { id -> viewModel.pause(downloadController, id) },
        resumeItem = { id -> viewModel.resume(downloadController, id) },
        removeItem = { id -> viewModel.remove(downloadController, id) },
        pauseAll = { viewModel.pauseAll(downloadController) },
        resumeAll = { viewModel.resumeAll(downloadController) },
        openDirectoryPicker = openDirectoryPicker
    )
}
