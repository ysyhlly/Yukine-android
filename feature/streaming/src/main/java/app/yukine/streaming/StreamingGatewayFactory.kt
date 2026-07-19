package app.yukine.streaming

import android.content.Context

interface StreamingGatewayFactory {
    fun remote(endpointBaseUrl: String): StreamingGateway
}

class RemoteStreamingGatewayFactory(
    private val localAuthStore: StreamingLocalAuthStore? = null,
    private val webCookieSessionSource: StreamingWebCookieSessionSource = NoopStreamingWebCookieSessionSource,
    private val luoxueSourceStore: LuoxueSourceStore? = null,
    private val kugouExperimentalSyncStore: KugouExperimentalSyncStore? = null
) : StreamingGatewayFactory {
    override fun remote(endpointBaseUrl: String): StreamingGateway {
        return RemoteStreamingGateway(
            endpointBaseUrl = endpointBaseUrl,
            localAuthStore = localAuthStore,
            webCookieSessionSource = webCookieSessionSource,
            luoxueSourceStore = luoxueSourceStore,
            kugouExperimentalSyncStore = kugouExperimentalSyncStore
        )
    }

    companion object {
        fun create(context: Context): RemoteStreamingGatewayFactory {
            return RemoteStreamingGatewayFactory(
                localAuthStore = LocalStreamingAuthStore(context),
                luoxueSourceStore = LuoxueSourceStore(context),
                kugouExperimentalSyncStore = KugouExperimentalSyncStore(context)
            )
        }
    }
}
