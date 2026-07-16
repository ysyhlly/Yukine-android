package app.yukine.common

import okhttp3.OkHttpClient

/**
 * Process-wide HTTP engine used by latency-sensitive application features.
 * Feature-specific clients should be created with [httpClient.newBuilder] so they retain the
 * same dispatcher, connection pool and DNS/TLS state while applying their own timeouts/cache.
 */
object ApplicationNetworkClient {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()
}
