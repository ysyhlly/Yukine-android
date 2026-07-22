package app.yukine

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import app.yukine.ui.EchoDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal enum class DiagnosticExportChoice {
    SAVE,
    SHARE
}

internal fun interface DiagnosticActivityResultLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal fun interface DiagnosticChoicePresenter {
    fun show(onChoice: (DiagnosticExportChoice) -> Unit)
}

internal fun interface DiagnosticTaskRunner {
    fun run(task: Runnable)
}

internal fun interface DiagnosticMainPoster {
    fun post(task: Runnable)
}

internal fun interface DiagnosticIntentStarter {
    fun start(intent: Intent)
}

internal fun interface DiagnosticShareUriProvider {
    fun uriFor(context: Context, file: File): Uri
}

internal interface DiagnosticExportOperations {
    fun createBundle(context: Context): File?
    fun copyTo(context: Context, bundle: File, uri: Uri): Boolean
}

internal class DiagnosticExportLauncher @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val statusSink: (String) -> Unit,
    private val languageModeProvider: () -> String = { AppLanguage.MODE_SYSTEM },
    activityResultLauncher: DiagnosticActivityResultLauncher? = null,
    private val operations: DiagnosticExportOperations = DefaultDiagnosticExportOperations,
    choicePresenter: DiagnosticChoicePresenter? = null,
    private val taskRunner: DiagnosticTaskRunner = DefaultTaskRunner,
    private val mainPoster: DiagnosticMainPoster = DiagnosticMainPoster { task ->
        activity.runOnUiThread(task)
    },
    private val intentStarter: DiagnosticIntentStarter = DiagnosticIntentStarter(activity::startActivity),
    private val shareUriProvider: DiagnosticShareUriProvider = FileProviderDiagnosticShareUriProvider
) {
    private val activityResultLauncher = activityResultLauncher
        ?: ActivityResultDiagnosticLauncher(activity)
    private val choicePresenter = choicePresenter
        ?: DialogDiagnosticChoicePresenter(activity, languageModeProvider)

    fun showOptions() {
        choicePresenter.show { choice ->
            when (choice) {
                DiagnosticExportChoice.SAVE -> save()
                DiagnosticExportChoice.SHARE -> share()
            }
        }
    }

    internal fun save() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(MIME_ZIP)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, bundleFileName())
        activityResultLauncher.launch(intent) { result ->
            val uri = result.data?.data
            if (result.resultCode != Activity.RESULT_OK || uri == null) return@launch
            statusSink("diagnostics.export.preparing")
            taskRunner.run(Runnable {
                val bundle = operations.createBundle(activity)
                val ok = bundle != null && operations.copyTo(activity, bundle, uri)
                postStatus(if (ok) "diagnostics.export.saved" else "diagnostics.export.failed")
            })
        }
    }

    internal fun share() {
        statusSink("diagnostics.export.preparing")
        taskRunner.run(Runnable {
            val bundle = operations.createBundle(activity)
            if (bundle == null) {
                postStatus("diagnostics.export.failed")
                return@Runnable
            }
            val uri = shareUriProvider.uriFor(activity, bundle)
            val send = Intent(Intent.ACTION_SEND)
                .setType(MIME_ZIP)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("diagnostics", uri)
            val chooser = Intent.createChooser(send, text("diagnostics.export.share.title"))
            mainPoster.post(Runnable {
                runCatching { intentStarter.start(chooser) }
                    .onSuccess { statusSink("diagnostics.export.shared") }
                    .onFailure { statusSink("diagnostics.export.failed") }
            })
        })
    }

    private fun postStatus(key: String) {
        mainPoster.post(Runnable { statusSink(key) })
    }

    private fun text(key: String): String = AppLanguage.text(languageModeProvider(), key)

    private fun bundleFileName(): String = "yukine-diagnostics-${timestamp()}.zip"

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private companion object {
        const val MIME_ZIP = "application/zip"

        private object DefaultTaskRunner : DiagnosticTaskRunner {
            private val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "DiagnosticExporter").apply { isDaemon = true }
            }

            override fun run(task: Runnable) {
                executor.execute(task)
            }
        }

        private object DefaultDiagnosticExportOperations : DiagnosticExportOperations {
            private val builder = DiagnosticBundleBuilder()

            override fun createBundle(context: Context): File? {
                val directory = File(context.cacheDir, "diagnostic_exports")
                directory.mkdirs()
                val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
                directory.listFiles().orEmpty()
                    .filter { it.isFile && it.lastModified() < cutoff }
                    .forEach { runCatching { it.delete() } }
                val output = File(
                    directory,
                    "yukine-diagnostics-${System.currentTimeMillis()}.zip"
                )
                return output.takeIf { builder.build(context, it) }
            }

            override fun copyTo(context: Context, bundle: File, uri: Uri): Boolean = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    bundle.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open destination")
                true
            }.getOrDefault(false)
        }

        private object FileProviderDiagnosticShareUriProvider : DiagnosticShareUriProvider {
            override fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        private class DialogDiagnosticChoicePresenter(
            private val activity: ComponentActivity,
            private val languageModeProvider: () -> String
        ) : DiagnosticChoicePresenter {
            override fun show(onChoice: (DiagnosticExportChoice) -> Unit) {
                EchoDialog.builder(activity)
                    .setTitle(text("diagnostics.export"))
                    .setMessage(text("diagnostics.export.privacy"))
                    .setItems(
                        arrayOf(
                            text("diagnostics.export.save"),
                            text("diagnostics.export.share")
                        )
                    ) { _, index ->
                        onChoice(if (index == 0) DiagnosticExportChoice.SAVE else DiagnosticExportChoice.SHARE)
                    }
                    .show()
            }

            private fun text(key: String): String =
                AppLanguage.text(languageModeProvider(), key)
        }

        private class ActivityResultDiagnosticLauncher(
            activity: ComponentActivity
        ) : DiagnosticActivityResultLauncher {
            private var callback: ((ActivityResult) -> Unit)? = null
            private val launcher = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result -> callback?.invoke(result) }

            override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
                callback = onResult
                launcher.launch(intent)
            }
        }
    }
}
