package app.yukine

import android.net.Uri

internal fun interface BackgroundImagePickedAction {
    fun apply(page: String, uri: Uri, transform: BackgroundTransform)
}

internal fun interface BackgroundImageCopyFailedAction {
    fun apply(page: String)
}

internal class BackgroundImagePickerBindings(
    private val pickedAction: BackgroundImagePickedAction,
    private val copyFailedAction: BackgroundImageCopyFailedAction
) : BackgroundImagePickerController.Listener {
    override fun backgroundImagePicked(page: String, uri: Uri, transform: BackgroundTransform) {
        pickedAction.apply(page, uri, transform)
    }

    override fun backgroundImageCopyFailed(page: String) {
        copyFailedAction.apply(page)
    }
}
