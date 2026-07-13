package app.yukine

import app.yukine.streaming.StreamingRepository

interface StreamingRepositorySource {
    fun current(): StreamingRepository
}

object EmptyStreamingRepositorySource : StreamingRepositorySource {
    override fun current(): StreamingRepository {
        return app.yukine.streaming.StreamingRepositoryFactory.empty()
    }
}
