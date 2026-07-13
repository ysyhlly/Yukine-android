package app.yukine

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBackgroundAccentControllerTest {
    @Test
    fun refreshRunsExtractionOffThreadAndAppliesOnMainThread() {
        val ioTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
        val applied = mutableListOf<Int?>()
        val controller = controller(ioTasks, mainTasks, applied)

        controller.refresh(PageBackgrounds(sharedUri = "content://blue"))

        assertTrue(applied.isEmpty())
        ioTasks.removeFirst().run()
        assertTrue(applied.isEmpty())
        mainTasks.removeFirst().run()
        assertEquals(listOf(0xFF247ACC.toInt()), applied)
    }

    @Test
    fun staleExtractionCannotOverwriteTheLatestAccent() {
        val ioTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
        val applied = mutableListOf<Int?>()
        val controller = controller(ioTasks, mainTasks, applied)

        controller.refresh(PageBackgrounds(sharedUri = "content://blue"))
        controller.refresh(PageBackgrounds(sharedUri = "content://green"))
        ioTasks.removeFirst().run()
        ioTasks.removeFirst().run()
        mainTasks.removeFirst().run()
        mainTasks.removeFirst().run()

        assertEquals(listOf(0xFF32A852.toInt()), applied)
    }

    private fun controller(
        ioTasks: ArrayDeque<Runnable>,
        mainTasks: ArrayDeque<Runnable>,
        applied: MutableList<Int?>
    ): CustomBackgroundAccentController = CustomBackgroundAccentController(
        operations = CustomBackgroundAccentOperations { backgrounds ->
            when (backgrounds.accentSourceUri()) {
                "content://blue" -> 0xFF247ACC.toInt()
                "content://green" -> 0xFF32A852.toInt()
                else -> null
            }
        },
        ioScheduler = CustomBackgroundAccentTaskScheduler(ioTasks::addLast),
        mainScheduler = CustomBackgroundAccentTaskScheduler(mainTasks::addLast),
        sink = CustomBackgroundAccentSink(applied::add)
    )
}
