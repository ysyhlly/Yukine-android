package app.yukine

import android.content.Context
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.ui.EchoDialog

internal interface DownloadQualityChooser {
    fun choose(title: String, onQualitySelected: (StreamingAudioQuality) -> Unit)
}

internal class DownloadQualityDialogController(
    private val context: Context,
    private val languageModeProvider: () -> String
) : DownloadQualityChooser {
    override fun choose(title: String, onQualitySelected: (StreamingAudioQuality) -> Unit) {
        val languageMode = languageModeProvider()
        val qualities = arrayOf(
            StreamingAudioQuality.STANDARD,
            StreamingAudioQuality.HIGH,
            StreamingAudioQuality.LOSSLESS,
            StreamingAudioQuality.HIRES
        )
        val labels = qualities.map {
            StreamingQualityPlatformMapping.optionLabel(it, languageMode)
        }.toTypedArray<CharSequence>()
        EchoDialog.builder(context)
            .setTitle(title)
            .setMessage(StreamingQualityPlatformMapping.downloadDialogMessage(languageMode))
            .setItems(labels) { _, which -> onQualitySelected(qualities[which]) }
            .setNegativeButton("取消", null)
            .show()
    }
}
