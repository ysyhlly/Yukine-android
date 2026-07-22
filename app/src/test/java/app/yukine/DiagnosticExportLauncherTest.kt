package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticExportLauncherTest {
    @Test
    fun saveLaunchesZipDocumentAndCopiesSuccessfulBundle() {
        val activity = activity()
        val resultLauncher = RecordingResultLauncher()
        val operations = RecordingOperations(activity)
        val statuses = mutableListOf<String>()
        val owner = launcher(activity, statuses, resultLauncher, operations)

        owner.save()

        val intent = resultLauncher.launches.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE)!!.startsWith("yukine-diagnostics-"))

        val destination = Uri.parse("content://diagnostics/export")
        resultLauncher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(destination)))

        assertEquals(listOf(destination), operations.destinations)
        assertEquals(
            listOf("diagnostics.export.preparing", "diagnostics.export.saved"),
            statuses
        )
    }

    @Test
    fun canceledSaveDoesNotCreateBundleOrEmitStatus() {
        val activity = activity()
        val resultLauncher = RecordingResultLauncher()
        val operations = RecordingOperations(activity)
        val statuses = mutableListOf<String>()
        val owner = launcher(activity, statuses, resultLauncher, operations)

        owner.save()
        resultLauncher.emit(ActivityResult(Activity.RESULT_CANCELED, null))

        assertEquals(0, operations.created)
        assertEquals(emptyList<String>(), statuses)
    }

    @Test
    fun shareUsesFileProviderUriClipDataAndReadGrant() {
        val activity = activity()
        val operations = RecordingOperations(activity)
        val statuses = mutableListOf<String>()
        val started = mutableListOf<Intent>()
        val owner = launcher(
            activity,
            statuses,
            RecordingResultLauncher(),
            operations,
            DiagnosticIntentStarter { started += it }
        )

        owner.share()

        val chooser = started.single()
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/zip", send.type)
        assertNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertNotNull(send.clipData)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(
            listOf("diagnostics.export.preparing", "diagnostics.export.shared"),
            statuses
        )
    }

    @Test
    fun showOptionsRoutesSelectedShareChoice() {
        val activity = activity()
        val operations = RecordingOperations(activity)
        val started = mutableListOf<Intent>()
        val presenter = DiagnosticChoicePresenter { callback ->
            callback(DiagnosticExportChoice.SHARE)
        }
        val owner = DiagnosticExportLauncher(
            activity = activity,
            statusSink = {},
            activityResultLauncher = RecordingResultLauncher(),
            operations = operations,
            choicePresenter = presenter,
            taskRunner = DiagnosticTaskRunner { it.run() },
            mainPoster = DiagnosticMainPoster { it.run() },
            intentStarter = DiagnosticIntentStarter { started += it },
            shareUriProvider = DiagnosticShareUriProvider { _, _ ->
                Uri.parse("content://app.yukine.fileprovider/diagnostic_exports/test.zip")
            }
        )

        owner.showOptions()

        assertEquals(1, started.size)
    }

    private fun launcher(
        activity: ComponentActivity,
        statuses: MutableList<String>,
        resultLauncher: DiagnosticActivityResultLauncher,
        operations: DiagnosticExportOperations,
        starter: DiagnosticIntentStarter = DiagnosticIntentStarter {}
    ): DiagnosticExportLauncher = DiagnosticExportLauncher(
        activity = activity,
        statusSink = { statuses += it },
        activityResultLauncher = resultLauncher,
        operations = operations,
        choicePresenter = DiagnosticChoicePresenter {},
        taskRunner = DiagnosticTaskRunner { it.run() },
        mainPoster = DiagnosticMainPoster { it.run() },
        intentStarter = starter,
        shareUriProvider = DiagnosticShareUriProvider { _, _ ->
            Uri.parse("content://app.yukine.fileprovider/diagnostic_exports/test.zip")
        }
    )

    private fun activity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private class RecordingResultLauncher : DiagnosticActivityResultLauncher {
        val launches = mutableListOf<Intent>()
        private var callback: ((ActivityResult) -> Unit)? = null

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            launches += intent
            callback = onResult
        }

        fun emit(result: ActivityResult) {
            callback?.invoke(result)
        }
    }

    private class RecordingOperations(private val context: Context) : DiagnosticExportOperations {
        var created = 0
        val destinations = mutableListOf<Uri>()

        override fun createBundle(context: Context): File {
            created++
            val directory = File(this.context.cacheDir, "diagnostic_exports").apply { mkdirs() }
            return File(directory, "test.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        }

        override fun copyTo(context: Context, bundle: File, uri: Uri): Boolean {
            destinations += uri
            return true
        }
    }
}
