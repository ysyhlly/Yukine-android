package app.yukine

import android.net.FakeUri
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundImageSelectionOwnerTest {
    @Test
    fun appliesPickedBackgroundToTheCurrentSettingsSnapshot() {
        val settingsViewModel = SettingsViewModel()
        val initial = PageBackgrounds(sharedUri = "file:///shared.jpg")
        val owner = BackgroundImageSelectionOwner(
            settingsViewModel = settingsViewModel,
            backgroundsSource = { initial },
            languageModeSource = { AppLanguage.MODE_ENGLISH },
            statusSink = { }
        )
        val transform = BackgroundTransform(scale = 1.5f, offsetX = 0.2f, offsetY = -0.1f)

        owner.backgroundImagePicked(
            PageBackgrounds.PAGE_HOME,
            FakeUri("file:///home.jpg"),
            transform
        )

        assertEquals("file:///shared.jpg", settingsViewModel.state.value.preferences.pageBackgrounds.sharedUri)
        assertEquals("file:///home.jpg", settingsViewModel.state.value.preferences.pageBackgrounds.homeUri)
        assertEquals(transform, settingsViewModel.state.value.preferences.pageBackgrounds.transformFor(PageBackgrounds.PAGE_HOME))
    }

    @Test
    fun localizesCopyFailureWithoutMutatingSettings() {
        val settingsViewModel = SettingsViewModel()
        val statuses = mutableListOf<String>()
        val owner = BackgroundImageSelectionOwner(
            settingsViewModel = settingsViewModel,
            backgroundsSource = { PageBackgrounds.empty() },
            languageModeSource = { AppLanguage.MODE_ENGLISH },
            statusSink = { statuses += it }
        )

        owner.backgroundImageCopyFailed(PageBackgrounds.PAGE_SETTINGS)

        assertEquals(
            listOf(AppLanguage.text(AppLanguage.MODE_ENGLISH, "page.background.copy.failed")),
            statuses
        )
        assertEquals(PageBackgrounds.empty(), settingsViewModel.state.value.preferences.pageBackgrounds)
    }
}
