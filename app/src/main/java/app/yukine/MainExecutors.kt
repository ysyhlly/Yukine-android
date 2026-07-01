package app.yukine

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal class MainExecutors {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val lyrics: ExecutorService = Executors.newFixedThreadPool(2)
    private val network: ExecutorService = Executors.newFixedThreadPool(3)

    fun io(task: Runnable) {
        executeSafely(io, task)
    }

    fun lyrics(task: Runnable) {
        executeSafely(lyrics, task)
    }

    fun network(task: Runnable) {
        executeSafely(network, task)
    }

    fun shutdownNow() {
        io.shutdownNow()
        lyrics.shutdownNow()
        network.shutdownNow()
    }

    private fun executeSafely(executor: ExecutorService, task: Runnable) {
        if (executor.isShutdown) {
            return
        }
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
        }
    }
}
