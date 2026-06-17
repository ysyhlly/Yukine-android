package app.yukine

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class MainExecutors {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val lyrics: ExecutorService = Executors.newFixedThreadPool(2)
    private val network: ExecutorService = Executors.newFixedThreadPool(3)

    fun io(task: Runnable) {
        io.execute(task)
    }

    fun lyrics(task: Runnable) {
        lyrics.execute(task)
    }

    fun network(task: Runnable) {
        network.execute(task)
    }

    fun shutdownNow() {
        io.shutdownNow()
        lyrics.shutdownNow()
        network.shutdownNow()
    }
}
