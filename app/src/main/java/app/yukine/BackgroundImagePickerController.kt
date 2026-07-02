package app.yukine

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts

internal fun interface BackgroundTaskRunner {
    fun run(task: Runnable)
}

internal fun interface BackgroundMainPoster {
    fun post(task: Runnable)
}

internal fun interface BackgroundDocumentPickerLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal fun interface BackgroundPreviewResultLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal class BackgroundImagePickerController @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val listener: Listener,
    private val ioRunner: BackgroundTaskRunner = BackgroundTaskRunner { task -> task.run() },
    private val mainPoster: BackgroundMainPoster = BackgroundMainPoster { task -> task.run() },
    private val languageModeProvider: () -> String = { AppLanguage.MODE_SYSTEM },
    private val transformProvider: (String) -> BackgroundTransform = { BackgroundTransform.IDENTITY },
    private val imageStore: BackgroundImageCopyStore = BackgroundImageStore(),
    documentPickerLauncher: BackgroundDocumentPickerLauncher? = null,
    previewResultLauncher: BackgroundPreviewResultLauncher? = null
) {
    interface Listener {
        fun backgroundImagePicked(page: String, uri: Uri, transform: BackgroundTransform)
        fun backgroundImageCopyFailed(page: String)
    }

    private val documentPickerLauncher = documentPickerLauncher ?: ActivityResultDocumentPickerLauncher(activity)
    private val previewResultLauncher = previewResultLauncher ?: ActivityResultPreviewLauncher(activity)
    private var pendingPage: String = ""
    private var pendingPreviewPage: String = ""
    private var pendingPreviewSourceUri: Uri? = null

    fun open(page: String) {
        val target = PageBackgrounds.normalizePage(page)
        pendingPage = target
        if (target.isBlank()) {
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        documentPickerLauncher.launch(intent) { result ->
            handleDocumentPicked(result)
        }
    }

    private fun handleDocumentPicked(result: ActivityResult) {
        val target = PageBackgrounds.normalizePage(pendingPage)
        pendingPage = ""
        val uri = result.data?.data
        if (result.resultCode != android.app.Activity.RESULT_OK || uri == null || target.isBlank()) {
            return
        }
        takePersistableReadPermission(result.data, uri)
        prepareInternalCopyAndPreview(target, uri)
    }

    private fun handlePreviewResult(result: ActivityResult) {
        val target = PageBackgrounds.normalizePage(pendingPreviewPage)
        val sourceUri = pendingPreviewSourceUri
        pendingPreviewPage = ""
        pendingPreviewSourceUri = null
        if (target.isBlank() || sourceUri == null) {
            return
        }
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val transform = BackgroundPreviewActivity.transformFromResult(result.data)
            persistChosenBackground(target, sourceUri, transform)
        }
    }

    private fun prepareInternalCopyAndPreview(page: String, source: Uri) {
        mainPoster.post(Runnable {
            pendingPreviewPage = page
            pendingPreviewSourceUri = source
            previewResultLauncher.launch(
                BackgroundPreviewActivity.intent(
                    activity,
                    source,
                    languageModeProvider(),
                    transformProvider(page)
                )
            ) { result ->
                handlePreviewResult(result)
            }
        })
    }

    private fun persistChosenBackground(page: String, source: Uri, transform: BackgroundTransform) {
        ioRunner.run(Runnable {
            val internalUri = imageStore.saveInternalCopy(activity.applicationContext, page, source)
            if (internalUri == null) {
                mainPoster.post(Runnable { listener.backgroundImageCopyFailed(page) })
                return@Runnable
            }
            mainPoster.post(Runnable {
                listener.backgroundImagePicked(page, internalUri, transform)
            })
        })
    }

    private fun takePersistableReadPermission(data: Intent?, uri: Uri) {
        val flags = data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                if (flags == 0) Intent.FLAG_GRANT_READ_URI_PERMISSION else flags
            )
        } catch (ignored: SecurityException) {
            // Some providers return readable URIs without granting persistable permission.
        } catch (ignored: IllegalArgumentException) {
            // Some providers return readable URIs without granting persistable permission.
        }
    }

    companion object {
        private class ActivityResultDocumentPickerLauncher(
            activity: ComponentActivity
        ) : BackgroundDocumentPickerLauncher {
            private var callback: ((ActivityResult) -> Unit)? = null
            private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                callback?.invoke(result)
            }

            override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
                callback = onResult
                launcher.launch(intent)
            }
        }

        private class ActivityResultPreviewLauncher(
            activity: ComponentActivity
        ) : BackgroundPreviewResultLauncher {
            private var callback: ((ActivityResult) -> Unit)? = null
            private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                callback?.invoke(result)
            }

            override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
                callback = onResult
                launcher.launch(intent)
            }
        }
    }
}
