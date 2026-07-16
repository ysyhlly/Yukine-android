package app.yukine.data

import app.yukine.data.room.ProviderResponseCacheEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderCachedResponse
import app.yukine.identity.ProviderEndpointHealth
import app.yukine.identity.ProviderResponseCacheRepository

class RoomProviderResponseCacheRepository(
    private val database: YukineDatabase,
    private val failureThreshold: Int = 3,
    private val circuitDurationMs: Long = 30L * 60L * 1_000L
) : ProviderResponseCacheRepository {
    private val dao = database.musicIdentityDao()

    override fun response(
        provider: String,
        endpoint: String,
        requestHash: String,
        now: Long
    ): ProviderCachedResponse? = dao.providerCache(provider, endpoint, requestHash)
        ?.takeIf { it.responseJson.isNotBlank() }
        ?.let {
            ProviderCachedResponse(
                provider = it.provider,
                endpoint = it.endpoint,
                requestHash = it.requestHash,
                responseJson = it.responseJson,
                createdAt = it.createdAt,
                expiresAt = it.expiresAt,
                freshness = if (it.expiresAt <= 0L || it.expiresAt > now) {
                    ProviderCacheFreshness.FRESH
                } else {
                    ProviderCacheFreshness.STALE
                }
            )
        }

    override fun endpointHealth(provider: String, endpoint: String): ProviderEndpointHealth =
        dao.providerCache(provider, endpoint, ENDPOINT_STATE_HASH).toHealth(provider, endpoint)

    override fun saveSuccess(
        provider: String,
        endpoint: String,
        requestHash: String,
        responseJson: String,
        now: Long,
        ttlMs: Long
    ) {
        require(requestHash.isNotBlank() && requestHash != ENDPOINT_STATE_HASH)
        require(responseJson.isNotBlank())
        database.runInTransaction {
            dao.upsert(
                ProviderResponseCacheEntity(
                    provider = provider,
                    endpoint = endpoint,
                    requestHash = requestHash,
                    responseJson = responseJson,
                    createdAt = now,
                    expiresAt = safeExpiresAt(now, ttlMs),
                    failureCount = 0,
                    circuitOpenUntil = 0L,
                    lastError = ""
                )
            )
            dao.upsert(endpointState(provider, endpoint, now))
        }
    }

    override fun recordFailure(
        provider: String,
        endpoint: String,
        error: String,
        now: Long
    ): ProviderEndpointHealth = database.runInTransaction<ProviderEndpointHealth> {
        val current = dao.providerCache(provider, endpoint, ENDPOINT_STATE_HASH)
        val nextFailures = (current?.failureCount ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1
        val openUntil = if (nextFailures >= failureThreshold) safeExpiresAt(now, circuitDurationMs) else 0L
        val updated = ProviderResponseCacheEntity(
            provider = provider,
            endpoint = endpoint,
            requestHash = ENDPOINT_STATE_HASH,
            responseJson = "",
            createdAt = current?.createdAt ?: now,
            expiresAt = 0L,
            failureCount = nextFailures,
            circuitOpenUntil = openUntil,
            lastError = error.take(MAX_ERROR_LENGTH)
        )
        dao.upsert(updated)
        updated.toHealth(provider, endpoint)
    }

    private fun endpointState(provider: String, endpoint: String, now: Long) = ProviderResponseCacheEntity(
        provider = provider,
        endpoint = endpoint,
        requestHash = ENDPOINT_STATE_HASH,
        responseJson = "",
        createdAt = now,
        expiresAt = 0L,
        failureCount = 0,
        circuitOpenUntil = 0L,
        lastError = ""
    )

    private fun ProviderResponseCacheEntity?.toHealth(
        provider: String,
        endpoint: String
    ): ProviderEndpointHealth = ProviderEndpointHealth(
        provider = provider,
        endpoint = endpoint,
        failureCount = this?.failureCount ?: 0,
        circuitOpenUntil = this?.circuitOpenUntil ?: 0L,
        lastError = this?.lastError.orEmpty()
    )

    private fun safeExpiresAt(now: Long, ttlMs: Long): Long {
        if (ttlMs <= 0L) return now
        return if (Long.MAX_VALUE - now < ttlMs) Long.MAX_VALUE else now + ttlMs
    }

    private companion object {
        const val ENDPOINT_STATE_HASH = "__endpoint_state__"
        const val MAX_ERROR_LENGTH = 1_024
    }
}
