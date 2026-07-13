package app.yukine

import android.content.ContentResolver
import java.util.concurrent.atomic.AtomicInteger

internal fun interface CustomBackgroundAccentOperations {
    fun extract(backgrounds: PageBackgrounds): Int?
}

internal fun interface CustomBackgroundAccentTaskScheduler {
    fun execute(task: Runnable)
}

internal fun interface CustomBackgroundAccentSink {
    fun apply(color: Int?)
}

internal class CustomBackgroundAccentController(
    private val operations: CustomBackgroundAccentOperations,
    private val ioScheduler: CustomBackgroundAccentTaskScheduler,
    private val mainScheduler: CustomBackgroundAccentTaskScheduler,
    private val sink: CustomBackgroundAccentSink
) {
    constructor(
        contentResolver: ContentResolver,
        ioScheduler: CustomBackgroundAccentTaskScheduler,
        mainScheduler: CustomBackgroundAccentTaskScheduler,
        sink: CustomBackgroundAccentSink
    ) : this(
        operations = CustomBackgroundAccentOperations { backgrounds ->
            CustomBackgroundAccentExtractor.extract(contentResolver, backgrounds)
        },
        ioScheduler = ioScheduler,
        mainScheduler = mainScheduler,
        sink = sink
    )

    private val refreshGeneration = AtomicInteger()

    fun refresh(backgrounds: PageBackgrounds) {
        val generation = refreshGeneration.incrementAndGet()
        ioScheduler.execute(Runnable {
            val color = operations.extract(backgrounds)
            mainScheduler.execute(Runnable {
                if (generation == refreshGeneration.get()) {
                    sink.apply(color)
                }
            })
        })
    }
}
