package app.echo.next

import app.echo.next.streaming.StreamingRepository

interface StreamingRepositorySource {
    fun current(): StreamingRepository
}

object EmptyStreamingRepositorySource : StreamingRepositorySource {
    override fun current(): StreamingRepository {
        return app.echo.next.streaming.StreamingRepositoryFactory.empty()
    }
}
