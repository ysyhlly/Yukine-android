package app.yukine.data

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Runs synchronous repository contract tests on the same kind of non-main thread used by owners. */
class BackgroundDatabaseTestRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit { base.evaluate() }.get()
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
