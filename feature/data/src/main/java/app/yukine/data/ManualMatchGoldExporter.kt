package app.yukine.data

import app.yukine.data.room.YukineDatabase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Read-only, in-process export of explicitly reviewed match decisions.
 *
 * The returned JSONL is never written, uploaded, or added to the repository by this class.
 * Pair IDs are salted for this export invocation and cannot be joined across separate exports.
 */
internal class ManualMatchGoldExporter(
    private val database: YukineDatabase,
    private val random: SecureRandom = SecureRandom()
) {
    fun exportJsonl(limit: Int = DEFAULT_LIMIT): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return database.musicIdentityDao()
            .identityOperationsByType(
                IdentityOperationType.MANUAL_MATCH_DECISION,
                limit.coerceIn(1, MAX_LIMIT)
            )
            .mapNotNull { operation ->
                val payload = runCatching { JSONObject(operation.afterPayload) }.getOrNull()
                    ?: return@mapNotNull null
                val label = payload.optString("label")
                    .takeIf { it in SUPPORTED_LABELS }
                    ?: return@mapNotNull null
                val left = payload.optJSONObject("left")?.sanitizedSide() ?: return@mapNotNull null
                val right = payload.optJSONObject("right")?.sanitizedSide() ?: return@mapNotNull null
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("pairId", pairId(salt, operation.id ?: return@mapNotNull null))
                    .put("label", label)
                    .put("left", left)
                    .put("right", right)
                    .put("note", payload.optString("note").trim().take(MAX_NOTE_LENGTH))
                    .toString()
            }
            .joinToString(separator = "\n")
    }

    private fun JSONObject.sanitizedSide(): JSONObject = JSONObject()
        .put("title", optString("title").trim())
        .put("artist", optString("artist").trim())
        .put("album", optString("album").trim())
        .put("albumArtist", optString("albumArtist").trim())
        .put("composer", optString("composer").trim())
        .put("releaseType", optString("releaseType").trim())
        .put("year", optInt("year").takeIf { it in 1000..9999 } ?: 0)
        .put("durationMs", optLong("durationMs").coerceAtLeast(0L))
        .put("versionSignature", optString("versionSignature").trim())

    private fun pairId(salt: ByteArray, operationId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update("manual-decision:$operationId".toByteArray(StandardCharsets.UTF_8))
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val DEFAULT_LIMIT = 5_000
        const val MAX_LIMIT = 50_000
        const val SALT_BYTES = 32
        const val MAX_NOTE_LENGTH = 500
        val SUPPORTED_LABELS = ManualMatchLabel.entries.mapTo(hashSetOf()) { it.name }
    }
}
