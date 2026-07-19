package app.yukine.streaming

import java.security.MessageDigest

data class StreamingPlaylistSyncSnapshot(
    val title: String,
    val orderedTrackIds: List<String>,
    val updatedAtMs: Long?,
    val deletedAtMs: Long? = null
) {
    val fingerprint: String
        get() {
            val canonical = buildString {
                append(title.trim())
                append('\n')
                orderedTrackIds.forEach {
                    append(it.trim())
                    append('\n')
                }
                append(deletedAtMs ?: "")
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
}

data class StreamingPlaylistPendingOperation(
    val operationType: String,
    val targetFingerprint: String,
    val confirmedItemCount: Int = 0,
    val retryCount: Int = 0,
    val nextAttemptAtMs: Long = 0L
) {
    val deduplicationKey: String
        get() = "$operationType:$targetFingerprint"
}

enum class StreamingPlaylistSyncWinner {
    NONE,
    LOCAL,
    REMOTE
}

object StreamingPlaylistSyncConflictResolver {
    fun resolve(
        baseline: StreamingPlaylistSyncSnapshot?,
        local: StreamingPlaylistSyncSnapshot,
        remote: StreamingPlaylistSyncSnapshot,
        remoteObservedChangeAtMs: Long? = null
    ): StreamingPlaylistSyncWinner {
        if (baseline == null) {
            return newerSideWins(local, remote, remoteObservedChangeAtMs)
        }

        val localChanged = local.fingerprint != baseline.fingerprint
        val remoteChanged = remote.fingerprint != baseline.fingerprint
        return when {
            !localChanged && !remoteChanged -> StreamingPlaylistSyncWinner.NONE
            localChanged && !remoteChanged -> StreamingPlaylistSyncWinner.LOCAL
            !localChanged && remoteChanged -> StreamingPlaylistSyncWinner.REMOTE
            else -> newerSideWins(local, remote, remoteObservedChangeAtMs)
        }
    }

    private fun newerSideWins(
        local: StreamingPlaylistSyncSnapshot,
        remote: StreamingPlaylistSyncSnapshot,
        remoteObservedChangeAtMs: Long?
    ): StreamingPlaylistSyncWinner {
        val localTime = maxOf(local.updatedAtMs ?: 0L, local.deletedAtMs ?: 0L)
        val remoteTime = maxOf(
            remote.updatedAtMs ?: remoteObservedChangeAtMs ?: 0L,
            remote.deletedAtMs ?: 0L
        )
        return if (localTime > remoteTime) {
            StreamingPlaylistSyncWinner.LOCAL
        } else {
            StreamingPlaylistSyncWinner.REMOTE
        }
    }
}

internal fun StreamingPlaylistSyncSnapshot.toJson(): org.json.JSONObject =
    org.json.JSONObject().apply {
        put("title", title)
        put("orderedTrackIds", org.json.JSONArray(orderedTrackIds))
        if (updatedAtMs == null) put("updatedAtMs", org.json.JSONObject.NULL)
        else put("updatedAtMs", updatedAtMs)
        if (deletedAtMs == null) put("deletedAtMs", org.json.JSONObject.NULL)
        else put("deletedAtMs", deletedAtMs)
    }

internal fun org.json.JSONObject.toSyncSnapshot(): StreamingPlaylistSyncSnapshot =
    StreamingPlaylistSyncSnapshot(
        title = optString("title"),
        orderedTrackIds = optJSONArray("orderedTrackIds")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty(),
        updatedAtMs = if (!has("updatedAtMs") || isNull("updatedAtMs")) null else optLong("updatedAtMs"),
        deletedAtMs = if (!has("deletedAtMs") || isNull("deletedAtMs")) null else optLong("deletedAtMs")
    )

internal fun StreamingPlaylistPendingOperation.toJson(): org.json.JSONObject =
    org.json.JSONObject().apply {
        put("operationType", operationType)
        put("targetFingerprint", targetFingerprint)
        put("confirmedItemCount", confirmedItemCount)
        put("retryCount", retryCount)
        put("nextAttemptAtMs", nextAttemptAtMs)
    }

internal fun org.json.JSONObject.toPendingOperation(): StreamingPlaylistPendingOperation? {
    val operationType = optString("operationType").takeIf(String::isNotBlank) ?: return null
    val targetFingerprint = optString("targetFingerprint").takeIf(String::isNotBlank) ?: return null
    return StreamingPlaylistPendingOperation(
        operationType = operationType,
        targetFingerprint = targetFingerprint,
        confirmedItemCount = optInt("confirmedItemCount", 0),
        retryCount = optInt("retryCount", 0),
        nextAttemptAtMs = optLong("nextAttemptAtMs", 0L)
    )
}
