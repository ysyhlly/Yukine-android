package app.yukine

internal class DownloadDirectoryPickerController(
    private val documentPickerProvider: DownloadDirectoryDocumentPickerProvider,
    private val feedbackSink: DownloadDirectoryPickerFeedbackSink
) {
    fun open() {
        val picker = documentPickerProvider.documentPicker()
        if (picker == null) {
            feedbackSink.showFeedback("目录选择暂不可用")
            return
        }
        picker.openDownloadFolderPicker()
    }
}

internal fun interface DownloadDirectoryPickerOpener {
    fun openDownloadFolderPicker()
}

internal fun interface DownloadDirectoryDocumentPickerProvider {
    fun documentPicker(): DownloadDirectoryPickerOpener?
}

internal fun interface DownloadDirectoryPickerFeedbackSink {
    fun showFeedback(message: String)
}
