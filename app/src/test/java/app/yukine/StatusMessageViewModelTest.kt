package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMessageViewModelTest {
    @Test
    fun applyStatusLocalizesAndPublishesState() {
        val viewModel = StatusMessageViewModel()

        val message = viewModel.applyStatus("Loading library", AppLanguage.MODE_CHINESE)

        assertEquals(AppLanguage.text(AppLanguage.MODE_CHINESE, "loading.library"), message)
        assertEquals(message, viewModel.state.value.message)
    }

    @Test
    fun localizePreservesUnknownMessages() {
        assertEquals("Ready", StatusMessageViewModel.localize("Ready", AppLanguage.MODE_CHINESE))
    }
}
