package app.yukine

import android.net.FakeUri
import app.yukine.di.PlatformModule
import org.junit.Assert.assertEquals
import org.junit.Test

class MainBackgroundImagePickerListenerTest {
    @Test
    fun delegatesBackgroundImageCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val listener = MainBackgroundImagePickerListener(
            pageImageApplier = BackgroundPageImageApplier { page, uri, transform ->
                calls += "picked:$page:$uri:${transform.scale}"
            },
            copyFailedStatusSink = BackgroundImageCopyFailedStatusSink { page ->
                calls += "failed:$page"
            }
        )

        listener.backgroundImagePicked("home", FakeUri("content://background/home"), BackgroundTransform(scale = 1.25f))
        listener.backgroundImageCopyFailed("settings")

        assertEquals(listOf("picked:home:content://background/home:1.25", "failed:settings"), calls)
    }

    @Test
    fun factoryCreatesBackgroundImagePickerControllerListener() {
        val factory = PlatformModule.provideMainBackgroundImagePickerListenerFactory()
        val calls = mutableListOf<String>()
        val listener = factory.create(
            BackgroundPageImageApplier { page, _, _ -> calls += "picked:$page" },
            BackgroundImageCopyFailedStatusSink { page -> calls += "failed:$page" }
        )

        listener.backgroundImagePicked("library", FakeUri("content://background/library"), BackgroundTransform.IDENTITY)
        listener.backgroundImageCopyFailed("queue")

        assertEquals(listOf("picked:library", "failed:queue"), calls)
    }
}
