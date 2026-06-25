package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDirectoryPickerControllerTest {
    @Test
    fun opensDocumentPickerWhenAvailable() {
        val picker = FakeDocumentPicker()
        val feedback = mutableListOf<String>()
        val controller = DownloadDirectoryPickerController(
            documentPickerProvider = DownloadDirectoryDocumentPickerProvider { picker },
            feedbackSink = DownloadDirectoryPickerFeedbackSink { feedback += it }
        )

        controller.open()

        assertEquals(1, picker.opens)
        assertEquals(emptyList<String>(), feedback)
    }

    @Test
    fun publishesFeedbackWhenDocumentPickerIsMissing() {
        val feedback = mutableListOf<String>()
        val controller = DownloadDirectoryPickerController(
            documentPickerProvider = DownloadDirectoryDocumentPickerProvider { null },
            feedbackSink = DownloadDirectoryPickerFeedbackSink { feedback += it }
        )

        controller.open()

        assertEquals(listOf("目录选择暂不可用"), feedback)
    }

    private class FakeDocumentPicker : DownloadDirectoryPickerOpener {
        var opens = 0

        override fun openDownloadFolderPicker() {
            opens += 1
        }
    }
}
