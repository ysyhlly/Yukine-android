package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundImagePickerControllerTest {
    @Test
    fun openUsesDocumentPickerAndKeepsOriginalUriForPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val previewLauncher = RecordingPreviewLauncher()
        val listener = RecordingListener()
        val store = FakeBackgroundImageStore()
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = listener,
            imageStore = store,
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = previewLauncher
        )

        controller.open(PageBackgrounds.PAGE_HOME)

        assertTrue(documentLauncher.launches.size == 1)
        assertEquals("image/*", documentLauncher.launches.single().type)
        assertTrue(previewLauncher.launches.isEmpty())

        documentLauncher.emit(
            ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://background/home"))
            )
        )

        assertEquals(1, previewLauncher.launches.size)
        assertEquals("content://background/home", previewLauncher.launches.single().getStringExtra("extra_background_uri"))
        assertTrue(store.saved.isEmpty())
        assertTrue(listener.picks.isEmpty())
    }

    @Test
    fun newlyPickedImagePreviewStartsAtOriginalFraming() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val previewLauncher = RecordingPreviewLauncher()
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = RecordingListener(),
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = previewLauncher
        )

        controller.open(PageBackgrounds.PAGE_HOME)
        documentLauncher.emit(
            ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://background/new-home"))
            )
        )

        val previewIntent = previewLauncher.launches.single()
        assertEquals(1f, previewIntent.getFloatExtra("extra_scale", -1f), 0f)
        assertEquals(0f, previewIntent.getFloatExtra("extra_offset_x", -1f), 0f)
        assertEquals(0f, previewIntent.getFloatExtra("extra_offset_y", -1f), 0f)
    }

    @Test
    fun backgroundTransformDoesNotAllowBlankPageEdgesAfterZoomOut() {
        val normalized = BackgroundTransform(scale = 0.5f, offsetX = 0.25f, offsetY = -0.25f).normalized()

        assertEquals(1f, normalized.scale, 0f)
    }

    @Test
    fun legacyThreePartBackgroundTransformKeepsLegacyCropLayout() {
        val decoded = BackgroundTransform.decode("1.75|0.25|-0.5")

        assertEquals(1.75f, decoded.scale, 0f)
        assertEquals(0.25f, decoded.offsetX, 0f)
        assertEquals(-0.5f, decoded.offsetY, 0f)
        assertEquals(BackgroundTransformLayout.LEGACY_CROP, decoded.layout)
        assertEquals("1.75|0.25|-0.5", decoded.encode())
    }

    @Test
    fun legacyThreePartBackgroundTransformRetainsHistoricalZoomOut() {
        val decoded = BackgroundTransform.decode("0.5|0.25|-0.25")

        assertEquals(0.5f, decoded.scale, 0f)
        assertEquals(BackgroundTransformLayout.LEGACY_CROP, decoded.layout)
        assertEquals("0.5|0.25|-0.25", decoded.encode())
    }

    @Test
    fun v2BackgroundTransformRoundTripsAsCropEditorLayout() {
        val original = BackgroundTransform(scale = 1.75f, offsetX = 0.25f, offsetY = -0.5f)

        assertEquals(BackgroundTransformLayout.CROP_EDITOR, BackgroundTransform.IDENTITY.layout)
        assertEquals("v2|1.75|0.25|-0.5", original.encode())
        assertEquals(original, BackgroundTransform.decode(original.encode()))
    }

    @Test
    fun v2BackgroundTransformDoesNotAllowHistoricalZoomOut() {
        val decoded = BackgroundTransform.decode("v2|0.5|0.25|-0.25")

        assertEquals(1f, decoded.scale, 0f)
        assertEquals(BackgroundTransformLayout.CROP_EDITOR, decoded.layout)
        assertEquals("v2|1.0|0.25|-0.25", decoded.encode())
    }

    @Test
    fun cropEditorTransformSurvivesPageBackgroundStorage() {
        val transform = BackgroundTransform(scale = 1.4f, offsetX = -0.3f, offsetY = 0.2f)

        val backgrounds = PageBackgrounds().withBackground(
            page = PageBackgrounds.PAGE_HOME,
            uri = "file:///internal/background.jpg",
            transform = transform
        )

        assertEquals("v2|1.4|-0.3|0.2", backgrounds.homeTransform)
        assertEquals(transform, backgrounds.transformFor(PageBackgrounds.PAGE_HOME))
    }

    @Test
    fun previewResultCopiesOriginalAndEmitsInternalImageAndTransform() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val previewLauncher = RecordingPreviewLauncher()
        val listener = RecordingListener()
        val store = FakeBackgroundImageStore()
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = listener,
            imageStore = store,
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = previewLauncher
        )
        val originalUri = Uri.parse("content://background/home")

        controller.open(PageBackgrounds.PAGE_HOME)
        documentLauncher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(originalUri)))
        previewLauncher.emit(
            ActivityResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra("extra_scale", 1.75f)
                    .putExtra("extra_offset_x", 0.25f)
                    .putExtra("extra_offset_y", -0.5f)
            )
        )

        assertEquals(
            listOf(
                Triple(
                    PageBackgrounds.PAGE_HOME,
                    Uri.parse("file:///internal/background.jpg"),
                    BackgroundTransform(1.75f, 0.25f, -0.5f)
                )
            ),
            listener.picks
        )
        assertEquals(listOf(originalUri), store.saved)
    }

    @Test
    fun previewCancelDoesNotCopyOrEmitPick() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val previewLauncher = RecordingPreviewLauncher()
        val listener = RecordingListener()
        val store = FakeBackgroundImageStore()
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = listener,
            imageStore = store,
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = previewLauncher
        )

        controller.open(PageBackgrounds.PAGE_HOME)
        documentLauncher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://background/home"))))
        previewLauncher.emit(ActivityResult(Activity.RESULT_CANCELED, Intent()))

        assertTrue(listener.picks.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun copyFailureAfterPreviewApplyReportsFailureWithoutEmittingPick() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val previewLauncher = RecordingPreviewLauncher()
        val listener = RecordingListener()
        val store = FakeBackgroundImageStore(resultUri = null)
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = listener,
            imageStore = store,
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = previewLauncher
        )

        controller.open(PageBackgrounds.PAGE_HOME)
        documentLauncher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://background/home"))))
        previewLauncher.emit(ActivityResult(Activity.RESULT_OK, Intent()))

        assertEquals(listOf(PageBackgrounds.PAGE_HOME), listener.failures)
        assertTrue(listener.picks.isEmpty())
    }

    @Test
    fun openIgnoresBlankPage() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val documentLauncher = RecordingDocumentPickerLauncher()
        val controller = BackgroundImagePickerController(
            activity = activity,
            listener = RecordingListener(),
            documentPickerLauncher = documentLauncher,
            previewResultLauncher = RecordingPreviewLauncher()
        )

        controller.open("")

        assertTrue(documentLauncher.launches.isEmpty())
    }

    private class RecordingListener : BackgroundImagePickerController.Listener {
        val picks = mutableListOf<Triple<String, Uri, BackgroundTransform>>()
        val failures = mutableListOf<String>()

        override fun backgroundImagePicked(page: String, uri: Uri, transform: BackgroundTransform) {
            picks += Triple(page, uri, transform)
        }

        override fun backgroundImageCopyFailed(page: String) {
            failures += page
        }
    }

    private class RecordingDocumentPickerLauncher : BackgroundDocumentPickerLauncher {
        val launches = mutableListOf<Intent>()
        private var callback: ((ActivityResult) -> Unit)? = null

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            callback = onResult
            launches += intent
        }

        fun emit(result: ActivityResult) {
            callback?.invoke(result)
        }
    }

    private class RecordingPreviewLauncher : BackgroundPreviewResultLauncher {
        val launches = mutableListOf<Intent>()
        private var callback: ((ActivityResult) -> Unit)? = null

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            callback = onResult
            launches += intent
        }

        fun emit(result: ActivityResult) {
            callback?.invoke(result)
        }
    }

    private class FakeBackgroundImageStore(
        private val resultUri: Uri? = Uri.parse("file:///internal/background.jpg")
    ) : BackgroundImageCopyStore {
        val saved = mutableListOf<Uri>()
        val deleted = mutableListOf<Uri>()

        override fun saveInternalCopy(context: Context, page: String, source: Uri): Uri? {
            saved += source
            return resultUri
        }

        override fun deleteInternalCopy(context: Context, uri: Uri?) {
            if (uri != null) {
                deleted += uri
            }
        }
    }
}
