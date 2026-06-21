package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogLanguageProviderBindingsTest {
    @Test
    fun providesLanguageModeToAllDialogControllers() {
        val binding = DialogLanguageProviderBindings { "zh" }

        val networkProvider: NetworkDialogController.LanguageProvider = binding
        val playlistProvider: PlaylistDialogController.LanguageProvider = binding
        val confirmationProvider: ConfirmationDialogController.LanguageProvider = binding

        assertEquals("zh", networkProvider.languageMode())
        assertEquals("zh", playlistProvider.languageMode())
        assertEquals("zh", confirmationProvider.languageMode())
    }
}
