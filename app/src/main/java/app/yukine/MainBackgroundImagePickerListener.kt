package app.yukine

import android.net.Uri

internal fun interface BackgroundPageImageApplier {
    fun apply(page: String, uri: Uri, transform: BackgroundTransform)
}

internal fun interface BackgroundImageCopyFailedStatusSink {
    fun setCopyFailedStatus(page: String)
}

internal fun interface MainBackgroundImagePickerListenerFactory {
    fun create(
        pageImageApplier: BackgroundPageImageApplier,
        copyFailedStatusSink: BackgroundImageCopyFailedStatusSink
    ): BackgroundImagePickerController.Listener
}

internal class MainBackgroundImagePickerListener(
    private val pageImageApplier: BackgroundPageImageApplier,
    private val copyFailedStatusSink: BackgroundImageCopyFailedStatusSink
) : BackgroundImagePickerController.Listener {
    override fun backgroundImagePicked(page: String, uri: Uri, transform: BackgroundTransform) {
        pageImageApplier.apply(page, uri, transform)
    }

    override fun backgroundImageCopyFailed(page: String) {
        copyFailedStatusSink.setCopyFailedStatus(page)
    }
}
