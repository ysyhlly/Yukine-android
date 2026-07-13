package app.yukine

import android.net.Uri

/** Owns applying a chosen background and presenting copy failures. */
internal class BackgroundImageSelectionOwner(
    private val settingsViewModel: SettingsViewModel,
    private val backgroundsSource: BackgroundsSource,
    private val languageModeSource: LanguageModeSource,
    private val statusSink: StatusSink
) : BackgroundImagePickerController.Listener {
    fun interface BackgroundsSource {
        fun current(): PageBackgrounds
    }

    fun interface LanguageModeSource {
        fun languageMode(): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    override fun backgroundImagePicked(page: String, uri: Uri, transform: BackgroundTransform) {
        settingsViewModel.applyPageBackgrounds(
            backgroundsSource.current().withBackground(page, uri.toString(), transform),
            page,
            false
        )
    }

    override fun backgroundImageCopyFailed(page: String) {
        statusSink.setStatus(
            AppLanguage.text(languageModeSource.languageMode(), "page.background.copy.failed")
        )
    }
}
