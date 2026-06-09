package app.echo.next.streaming

import android.content.Context

interface StreamingGatewayFactory {
    fun remote(endpointBaseUrl: String): StreamingGateway
}

class RemoteStreamingGatewayFactory(
    private val localAuthStore: StreamingLocalAuthStore? = null
) : StreamingGatewayFactory {
    override fun remote(endpointBaseUrl: String): StreamingGateway {
        return RemoteStreamingGateway(
            endpointBaseUrl = endpointBaseUrl,
            localAuthStore = localAuthStore
        )
    }

    companion object {
        fun create(context: Context): RemoteStreamingGatewayFactory {
            return RemoteStreamingGatewayFactory(LocalStreamingAuthStore(context))
        }
    }
}
