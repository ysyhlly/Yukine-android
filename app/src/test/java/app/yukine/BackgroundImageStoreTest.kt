package app.yukine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundImageStoreTest {
    @Test
    fun replacingTheSamePageBackgroundReturnsANewInternalUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BackgroundImageStore()
        val firstSource = createSourceImage(context, Color.RED)
        val secondSource = createSourceImage(context, Color.BLUE)
        var firstCopy: Uri? = null
        var secondCopy: Uri? = null

        try {
            val first = requireNotNull(
                store.saveInternalCopy(context, PageBackgrounds.PAGE_HOME, Uri.fromFile(firstSource))
            )
            firstCopy = first
            val second = requireNotNull(
                store.saveInternalCopy(context, PageBackgrounds.PAGE_HOME, Uri.fromFile(secondSource))
            )
            secondCopy = second

            assertNotEquals(first, second)
            assertTrue(File(first.path.orEmpty()).isFile)
            assertTrue(File(second.path.orEmpty()).isFile)
        } finally {
            store.deleteInternalCopy(context, firstCopy)
            store.deleteInternalCopy(context, secondCopy)
            firstSource.delete()
            secondSource.delete()
        }
    }

    private fun createSourceImage(context: Context, color: Int): File {
        val file = File.createTempFile("background-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
